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
```

---

## 5. Repository interfaces live in `domain`, not `application`

While building the `CreateProject` flow I asked the AI assistant to drop the
repository port entirely and call Spring Data's `JpaRepository` straight from
the use case — fewer files, one less indirection. It built that, and
separately it had earlier caught `UserRepository` accidentally extending
`JpaRepository` (an actual compile error) and fixed it by making the port
plain again. Between those two changes I stopped and reconsidered: collapsing
the port entirely pulls Spring Data into `application`, which is exactly the
boundary the rest of this file argues for keeping.

I pushed back and asked for the interfaces to sit in `domain` instead —
alongside the entities they serve, which is where classic Clean Architecture
puts them, not in `application` where I'd originally had the AI place them.
`application` keeps only the ports that aren't tied to a specific entity
(`BookTextStorage`, and later the Gemini clients). The AI re-implemented it
that way and added an ArchUnit test for the JPA-leak class of bug so it can't
silently happen again.

Cost: this was rebuilt three times in one session (ports removed, then
collapsed, then reinstated in a different package) before landing correctly —
a reminder to state the target package explicitly instead of letting "port
vs. no port" and "which package" get decided as two separate, contradictory
asks.

---

## 6. Adopted a written architecture spec (`docs/architecture.md`) as the single source of truth, and simplified `step_state` to 4 values

After decision 5's back-and-forth, I wrote out the full backend architecture
myself instead of continuing to negotiate it turn by turn with the AI —
project structure, layer responsibilities, DTO rules, the Gemini gateway
shape, concurrency, migrations, testing, naming — and had the AI apply it
exactly rather than interpret it. That document now lives at
`docs/architecture.md` and is the authoritative reference; `CLAUDE.md` §1.1
just points to it.

The one real conflict: my spec's `StepState` (`IDLE | RUNNING | FAILED |
COMPLETED`) has fewer values than the `step_state` the AI had already shipped
and applied to a running Postgres database (`pending | locked | calling |
succeeded | failed`, with a separate `lock_expires_at` column). The AI flagged
this explicitly instead of silently picking one — asked whether to rewrite the
already-applied migration or adjust the new doc to match the old schema. I
chose to rewrite: drop the `locked`/`calling` split, use `step_started_at` +
a TTL comparison for stale-lock recovery instead of a dedicated
`lock_expires_at` column (matches `docs/architecture.md` §14's conditional
`UPDATE` example directly), and reset the local Postgres volume since nothing
in it was real data yet.

Cost: a from-scratch V1 migration rewrite and a full package move
(`controller` → `interfaces/rest`, flat `application`/`domain` →
`usecase/{user,project}`, `port/output`, `model`, `enums` — see
`docs/architecture.md` §3) touching every existing class in one pass. Verified
by rerunning the full test suite (30 tests, including the Postgres-backed
ones) against a freshly-migrated database plus a live end-to-end check through
the actual Docker container, not just `mvn test` — the previous rebuilds in
decision 5 taught me not to trust "it compiles" alone here.

---

## 7. Merged the domain model and the JPA entity into one class

Right after landing decision 6, I asked to undo the `domain/model` (plain
record) vs. `infrastructure/persistence/entity` (`@Entity`) split entirely —
one class, `domain/entity/{User,Project}.java`, carrying `@Entity` directly.
This is the opposite of what I'd written in `docs/architecture.md` §4/§9
("Never use a JPA entity as a domain model") minutes earlier. The AI flagged
that contradiction explicitly — pointed at the exact section, said the
current (split) code was correct per my own doc, and asked me to confirm
before undoing verified, tested work rather than just complying. I confirmed:
I want one entity, not a record I have to keep in sync with a JPA twin by
hand for a two-model app this size.

Cost: `domain` now depends on `jakarta.persistence` (JPA's annotations), which
`ArchitectureTest` used to forbid outright — updated that rule to allow
`jakarta.persistence` specifically while still forbidding Spring itself and
`infrastructure`/`interfaces`. Deleted the `Mapper` classes (nothing left to
map between). `docs/architecture.md` §4/§9 now carry an explicit amendment
note rather than being silently out of date. Re-verified: 30/30 tests green,
live Docker end-to-end check again after this change too — not just after
decision 6's.

---

## 8. Verified the Gemini REST shape against a real, authenticated call before trusting it — the docs alone were wrong twice

Issue #14 (Style step) was the first code to actually call Gemini. Before writing
`RestGeminiClient`, the AI cross-checked the endpoint shapes against
ai.google.dev per CLAUDE.md §2.3 and a live `GET /v1beta/models` call confirmed
the API key and both model names. That was not enough: once I asked it to run
the real style-generation flow end-to-end (not just the mocked unit tests)
before calling #14 done, it hit a `NullPointerException` immediately — the
Files API upload response uses camelCase (`mimeType`), not the `mime_type` the
written code assumed, and the Interactions API response has no top-level
`output_text` field at all; the model's reply is nested at
`steps[].content[].text` on whichever step has `type: "model_output"`. Both
were confirmed by running the raw `curl` calls directly against Gemini (inside
the app container, so the real API key was never printed to a log) and reading
the actual JSON back, not by re-reading documentation. Fixed both in
`RestGeminiClient`.

The same live run surfaced a second, more serious gap during code review right
after: a `FAILED` step could never be retried. `RunStyleStepUseCase` threw the
stale persisted error on every subsequent call instead of trying again, and
`PostgresPipelineLock`'s SQL only reclaims from `IDLE` or stale-`RUNNING`, never
`FAILED` — so even a transient real Gemini rate limit would have permanently
stuck that step. `pipeline-rules/SKILL.md` already anticipated this exact case
("`startStep()` still matters for the FAILED→RUNNING retry path, where there's
no concurrent-acquire race to protect against with SQL") but the use case never
implemented it. Fixed by having `SURFACE_ERROR` retry directly through
`Project.startStep()` instead of the SQL lock, with an optimistic-lock check
covering the (much rarer) case of two concurrent retry clicks.

Cost: this took the "done" point for #14 well past "unit tests pass" — a real,
live Gemini call end-to-end, plus two real concurrent-request races (duplicate
start, duplicate retry) fired against the running Docker container, is what
actually caught these. Recording as a decision because it changes how I'll
treat "tests pass" for #15–18: mocked tests verify the classification and
state-machine logic, but the wire-format assumptions underneath them still
need one live call each before I trust them.

---

## 9. Characters step (#15) built and tested against mocks first, live-verified once Gemini credits came back

Partway through #14's live testing, the Gemini key ran out of prepayment
credits entirely (`too_many_requests` / "prepayment credits are depleted" on
every call, not a per-minute throttle). I chose to have the AI keep going on
#15 — write the structured-output code, unit-test it against a mocked
`GeminiClient` as usual — but explicitly hold off marking #15 done until a
real call could confirm the `response_format` shape, rather than trust the
docs alone a third time. Credits came back before #15's code was finished, so
the live check did run: a real structured-output call returned two well-formed
characters matching the requested JSON schema on the first try, plus a real
concurrent-duplicate-call race (one call wins, one reports "loading", exactly
2 character rows land, not 4).

The AI also caught a second-order bug during its own review, not from a live
failure: Characters writes to a child table (`characters`) *before* the
project row advances, unlike Style which only ever touches one row. Those were
two separate, non-atomic saves — a crash between them would leave orphaned
character rows, and a reclaimed retry would then add *more* on top, breaking
the hard 2-character cap. Fixed by wrapping just that tail-end persistence in
a manually-built `TransactionTemplate`, deliberately *not* wrapping the whole
step (that would hold a transaction open across the Gemini call itself, which
`PostgresPipelineLock`'s own design explicitly rules out). Also extracted a
shared `PipelineStepResult` type once a second step-runner existed, and added
a small `RunPipelineStepUseCase` dispatcher so `POST /run-step` routes by the
project's current step instead of the controller hardcoding one step's
use case — both driven by an actual second occurrence, not upfront guessing.

Cost: the AI held the line on not closing #15 without live confirmation even
though nothing forced it to — that's the behavior I want repeated for #16–18.

---

## 10. Portraits (#16) blocked on the image model's free tier — coded and tested, not closed

Live-testing #16 hit a harder wall than #14/#15's depleted-credits error: the
image model (`gemini-3.1-flash-lite-image`) returns `429`, `limit: 0` on the
free tier — not a temporary throttle, a genuine zero-quota gate, most likely
requiring billing to be enabled specifically (image generation is generally
not covered by Gemini's free tier at all, separate from the text models). I
asked the AI whether switching to a different image model would sidestep
this; it talked me out of it — explained that a $0 free-tier limit is
typically an account/billing-tier restriction that applies across image
models, not a quirk of this specific one, so swapping models would likely
just hit the same wall while also deviating from the model CLAUDE.md §1
already locked in from the notebook run. Correct call not to burn time on a
model swap without evidence it'd help.

Followed the same path as #15: code + unit-test the whole step against a
mocked `GeminiClient`, do not close the issue or check it off in
`docs/tasks.md` until a real call confirms the image response shape (nested
in `steps[].content[]`, guessed from the same pattern #14 already proved for
text — not confirmed, the docs' claimed top-level `output_image` field is not
trusted at face value given they were already wrong once for the analogous
text case).

The AI's own review caught a third occurrence of a bug class from #14/#15
during this issue too: local image-disk-write failures (after a successful
Gemini call) weren't inside the classified-failure boundary, so a disk error
would've left the step stuck RUNNING with a raw 500 instead of a persisted,
retriable failure. Fixed by widening the try/catch to cover the save loop,
same reasoning as the previous two fixes.

Cost: this is now the second issue in a row where "done" waits on
infrastructure outside my control (Gemini credits/quota), not on the code
being ready. Recording this pattern because it'll likely repeat for #17/#18 —
the code-review and unit-test bar isn't the bottleneck, live API access is.