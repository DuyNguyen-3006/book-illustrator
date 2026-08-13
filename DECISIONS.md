# DECISIONS.md — book-illustrator

Decisions only. This file records the important technical decisions made during
development, including where I disagreed with an AI coding assistant and why.
Git history is used for implementation history and worklog details.

---

# Decisions

## 1. Java + Spring Boot with PostgreSQL instead of Node.js + JSON files

The initial AI-generated setup used Node.js + Express with JSON files because
the assessment explicitly allows a small and familiar stack. I pushed back on
both choices. Java + Spring Boot is the stack I am most productive with, and
PostgreSQL gives me transactions, constraints, and concurrency primitives that
are useful for the pipeline's execution rules.

I chose a modular Spring Boot application with PostgreSQL rather than
microservices or a file-based store. PostgreSQL is run locally through
Docker Compose, so the database does not introduce a hosted dependency.

The cost is more setup than a JSON-file implementation, but the database makes
the pipeline state and duplicate-execution protection much easier to reason
about and test.

---

## 2. Clean Architecture, but only where it provides a useful boundary

I chose Clean Architecture for the backend because the pipeline rules are more
important than the HTTP or persistence details. The domain and application
layers contain the pipeline state transitions and business rules, while
Spring/JPA and Gemini integration remain infrastructure concerns.

The AI assistant pushed back on this choice by pointing out that a five-step
pipeline and a small number of screens do not justify unnecessary layers or
interfaces with no real purpose. I agreed with that concern and kept the
architecture deliberately small: domain models, application use cases and
ports, infrastructure adapters, and REST interfaces.

The cost is some additional mapping and boilerplate. I am accepting that cost
because it keeps the state-machine logic independent from Spring and makes the
most important rules easier to unit test. If an abstraction becomes only
ceremony during implementation, I will remove it rather than keeping it for
the sake of the architecture diagram.

---

## 3. Separate overall project status from pipeline step state

The AI assistant initially suggested using a single status value to represent
the project's progress. I pushed back because a single field cannot clearly
represent both the overall lifecycle and the execution state of the current
pipeline step.

For example, after a refresh the application needs to know that a project is
still in progress, that the current step is `PORTRAITS`, and that the portraits
step is currently running. I therefore separated these concepts into:

- `status` — the overall project lifecycle.
- `current_step` — which pipeline step the project is currently at.
- `step_state` — whether that step is idle, running, failed, or completed.

The cost is that multiple fields have to be updated consistently. I keep the
state-transition rules in the application layer and cover the important
transitions with tests so that invalid states are not created accidentally.

---

## 4. Conditional UPDATE for pipeline locking instead of holding a database lock during Gemini calls

The duplicate-execution requirement is important because refreshing the page,
opening a second tab, or double-clicking a generate button must not result in
multiple Gemini requests for the same step.

I initially considered using `SELECT ... FOR UPDATE` around the whole pipeline
operation. The AI assistant pointed out that this would keep a PostgreSQL
transaction and connection open while waiting for the Gemini API, which can
take significantly longer than the database update itself.

I therefore chose an atomic conditional `UPDATE` to acquire the step:

```sql
UPDATE projects
SET step_state = 'RUNNING',
    step_started_at = NOW()
WHERE id = ?
  AND step_state = 'IDLE';