# CLAUDE.md — book-illustrator

> Gradion Intern Fullstack Developer take-home assessment. Full spec: `docs/ASSESSMENT.md`
> (paste the original assessment text there if not already present). This file is the
> working contract for the AI — read it before touching code.

---

## 0. What this is

A web app that turns a book's text into character portraits and a chapter illustration
via the Gemini API. Five user-driven steps: **Style → Characters → Portraits → Chapters →
Illustrations**. Reference pipeline: run
`Book_illustration.ipynb` (Google Gemini cookbook, "Illustrate a book: The Wind in the
Willows", steps 1–5) in Colab **before writing any app code** — the mechanics (which
model, which call, context chaining, structured output) come from there and from
https://ai.google.dev/gemini-api/docs, never from guesswork.

Budget: ~16 focused hours, 3-day deadline. **Keep it simple and lean — do not
over-engineer.** This is a graded criterion (§07 "Right-sized solution"), not a platitude.

---

## 1. Stack

| Layer | Choice | Notes |
|---|---|---|
| Backend | Java + Spring Boot | User's own call, overriding the initial Node.js proposal — Java is the developer's familiar/fast language, which is what spec §5.1 "boring and familiar" actually means (familiar *to the builder*, not a fixed recommendation). Spring Boot is the default framework choice; change it and note why if picking something else. **Caveat:** the Gemini "interactions" (chat-chaining) API used by the reference notebook only has Python/JS SDKs — Java calls it over REST directly, see `.claude/skills/pipeline-rules/SKILL.md` §5 |
| Architecture | Clean Architecture (entities / use cases / interface adapters / frameworks) | User's choice. Watch spec §05 "do not over-engineer" and §07 "Right-sized solution" — layer boundaries must earn their keep for a 5-step pipeline + 4 screens, not be ceremony. If a layer adds indirection without a real reason (e.g. a use-case interface with exactly one implementation and no planned second one), collapse it |
| Frontend | React + Vite (TypeScript) | Matches `app-demo.html` reference scope |
| Storage | PostgreSQL, local via `docker-compose.yml` | Spec §5.2 allows either a DB or JSON files; chose Postgres for real transactions instead of hand-rolled file locking. Fully local/offline — matches the "local only" deploy constraint, no hosted DB account needed to run or grade this. Locking for pipeline steps: `SELECT ... FOR UPDATE` or a unique constraint on `(project_id, step_id)` — see `.claude/skills/pipeline-rules/SKILL.md` §2. Images and book text still live on the local filesystem per spec §5.2 (no S3/blob storage) — DB stores metadata + paths, not file bytes |
| Queue | none | Steps run inline, awaited synchronously per request; no background workers |
| Gemini text model | `gemini-3.6-flash` | Notebook's selected default, confirmed by a real run (`Book_illustration.ipynb`). Still check current free-tier limits at ai.google.dev/gemini-api/docs before starting (§5.3) |
| Gemini image model | `gemini-3.1-flash-lite-image` | Notebook's selected default (Nano Banana family), confirmed by a real run. Check image free-tier limits — tighter than text |
| Auth | Email + name only, session cookie. No password, no OAuth (spec §4.1) | |
| Deploy | **Local only**, via `docker-compose.yml` (app + Postgres). Spec explicitly forbids public deployment (§08) — a hosted demo risks leaking the Gemini key and won't be credited | |

**This stack is closed for this assessment.** Anything not in the table above counts as
"not approved" — see §2.1. Backend/storage/architecture rows above were changed from the
original proposal on 2026-08-13 — logged in `DECISIONS.md` (§2.1 below — required entry:
"stack and storage choice").

### 1.1 Backend folder layout (Clean Architecture, 4 top-level packages)

```
backend/src/main/java/.../
├── controller/       ← REST controllers, request/response DTOs
├── application/      ← use cases + port interfaces (what infra must implement)
├── domain/           ← entities, value objects, the pipeline state machine — no
│                        framework imports here at all (no Spring, no JPA, no Jackson)
└── infrastructure/   ← JPA repositories/entities, the Gemini REST client, Postgres
                         lock implementation, filesystem image/text storage
```

Dependency direction is one-way, inward: `controller` and `infrastructure` depend on
`application`; `application` depends on `domain`; `domain` depends on nothing else in this
list. `controller` never calls `infrastructure` directly — only through an `application`
use case. Concretely for this project:

- `domain` — the `status`/`step_state` state machine and its legal-transition rules
  (`.claude/skills/pipeline-rules/SKILL.md` §1), the 2-character/1-chapter cap validation,
  plain Java records/classes only.
- `application` — one use case per pipeline step (`RunStyleStep`, `RunCharactersStep`,
  ...) plus port interfaces they depend on: `ProjectRepository`, `PipelineLock`,
  `GeminiTextClient`, `GeminiImageClient`. Application code imports these as interfaces,
  never their concrete implementation.
- `infrastructure` — Spring Data JPA implementations of `ProjectRepository`, a
  `PipelineLock` backed by `SELECT ... FOR UPDATE` / a unique constraint on
  `(project_id, step_id)`, the actual REST calls to Gemini (§2.3 research first), local
  filesystem read/write for images and book text.
- `controller` — maps HTTP requests to use-case calls, builds the response envelope
  (`.claude/skills/backend-rules/SKILL.md` §1). No business logic here.

**Enforce this with a real test, don't just describe it in prose:** an ArchUnit rule (or
equivalent) asserting `domain` has zero dependencies on `infrastructure`/`controller`/
Spring/JPA is cheap to write and is the actual proof the layering isn't just folder names
— add it as part of Milestone 2 (#5), not as an afterthought.

If a use case ends up as a one-line pass-through to a repository with no domain logic,
that's a signal to collapse it, not a missing abstraction to add elsewhere — see the
over-engineering note in the Architecture row above.

---

## 2. Mandatory rules

### 2.1 Never add a dependency or framework on your own
Do NOT install any package, framework, ORM, state-management library, UI kit, etc.
outside the stack in §1 without asking first. When something looks necessary: state what's
needed → why the current stack/stdlib is not enough → 2 options → your recommendation, then
ask. After approval, log it in `DECISIONS.md`, then install.

### 2.2 Never auto-retry a Gemini call in a loop
Spec §4.3, verbatim requirement: "Never auto-retry a Gemini call in a loop — retries are
user-triggered only." Every Gemini call runs **exactly once** per user action. On failure:
persist the failure, surface a meaningful error, stop. No `for attempt in range(n)`, no
exponential backoff, not even for 429/503/timeout. A user clicking "Retry" in the UI is a
new user-initiated call and is always allowed — see `.claude/skills/pipeline-rules/SKILL.md`
for how that interacts with the per-step lock.

### 2.3 Research before writing any Gemini API code
Before writing a line of code that touches the Gemini API (client, prompts, response
schema, streaming, context chaining, image generation, rate limits, error codes):

1. Confirm it matches what the reference notebook actually does for that step — re-open
   `Book_illustration.ipynb` if unsure.
2. Cross-check the concrete REST shape (endpoint, params, structured-output schema) against
   https://ai.google.dev/gemini-api/docs — the notebook uses SDKs; the newest conversation
   API is only SDK-wrapped for Python/JS so far, so non-Python/JS stacks map notebook calls
   to their REST equivalents (spec's explicit hint).
3. Never invent a method name, field name, or model name from memory. If genuinely unclear,
   ask the user rather than guessing.
4. Note in the relevant commit/task what was checked and how it was applied.

This also applies when switching models or changing a request/response shape mid-project.

### 2.4 Task-driven workflow: Plan → Test-first → Code → Review → Done
Tasks are tracked as **GitHub Issues** in `DuyNguyen-3006/book-illustrator`, grouped into
8 milestones and labeled `backend` / `frontend` / `docs` / `setup` / `qa`, mirrored as a
flat checklist in `docs/tasks.md` (`#N` = issue number, `[BE]`/`[FE]`/etc. = label).
Filter with `gh issue list --repo DuyNguyen-3006/book-illustrator --label backend` (swap
the label to work FE-only, docs-only, etc.). Every issue goes through all five stages
below — each has its own rule, none are optional:

```
PICK   gh issue list --repo DuyNguyen-3006/book-illustrator --state open --milestone "<n>"
       pick the next open issue in milestone order; only one issue "in progress" at a time
        ↓
PLAN   Before writing any code: 2–4 lines — what changes, which files, what the
       done-state looks like. For anything touching the pipeline/state machine, this
       includes which rows of `.claude/skills/pipeline-rules/SKILL.md` apply. Post it as
       a comment on the issue (`gh issue comment <N> --body "..."`) or as the first line
       of the eventual commit body — pick one and be consistent. Skip only for truly
       trivial issues (e.g. `./test.sh`, `.env.example`) — say so, don't skip silently.
        ↓
TEST   Write the failing test first (spec §09.5: "make it write the test first, then the
       code — a leash on the AI, not a coverage target"). State-machine/lock/resume logic
       → see backend-rules §3 for required coverage. UI components → frontend-rules §1
       (loading/error/empty).
        ↓
CODE   RESEARCH first whenever Gemini/API surface is touched (§2.3). Then implement,
       strictly inside this issue's scope, until the test from the previous step passes.
        ↓
REVIEW `code-review-expert` on the diff. All P0/P1 findings fixed in-scope before moving
       on — see §3 for the severity gate. P2/P3: fix now or open a new issue, don't defer
       silently.
        ↓
DONE   Commit (§2.5, reference `Closes #N` in the message body so the issue closes with
       the merge/commit), check the box in docs/tasks.md, close the issue if `Closes #N`
       didn't already do it via `gh issue close <N>` (only after the above stages, not
       before).
```

Constraints:
- **No coding outside the active issue's scope.** Spotted a bug or improvement elsewhere →
  `gh issue create --repo DuyNguyen-3006/book-illustrator --title "..."` (add to the
  right milestone, or a new one); don't fix it in passing.
- An issue too large for one sitting → split it into sub-issues before starting, close the
  original as "split into #X, #Y".
- This is intentionally still lean — 5 fixed stages, no swimlanes, no auto-assignment, no
  bot automation. Matches spec §05 "do not over-engineer"; grow it only if it's actually
  getting in the way.

### 2.5 Commits
- Small, one idea each. **No single giant commit** (spec §2.4, graded).
- Commit **as you go**, not all at the end — timestamps are looked at.
- Message: `<type>(<scope>): <short imperative summary>` (`feat`, `fix`, `refactor`,
  `test`, `docs`, `chore`).
- If a commit is mostly AI-authored, say so in the body, e.g.:
  ```
  AI-Authored: <file paths, or "entire diff">
  AI-Reviewed-By: <your name>
  ```
  Honesty scores here; hiding it doesn't (spec §2.4).
- Never commit secrets, `.env`, or API keys. Ship `.env.example` instead.

### 2.6 Log decisions in DECISIONS.md as they happen — free prose, not a template
Spec §2.1 is explicit: **"No template to fill in."** A heading per decision, then a short
paragraph in your own words — who proposed it, who pushed back, where you landed, what it
cost. Write it the moment the decision is made, not batched at the end — vague or
obviously back-filled entries score badly. See `DECISIONS.md` for the exact format and the
required topics (stack/storage, pipeline progress modeling, duplicate-call prevention,
plus ≥3 places you overrode the AI).

---

## 3. Skill routing

- **`.claude/skills/pipeline-rules/SKILL.md`** — state machine (`status`/`step_state`),
  per-step locking, resume behaviour, the 2 character / 1 chapter caps. Read before
  touching any pipeline-step code.
- **`.claude/skills/backend-rules/SKILL.md`** — BE↔FE response envelope, error handling
  around Gemini calls, required test coverage for state-machine logic.
- **`.claude/skills/frontend-rules/SKILL.md`** — mandatory loading/error/empty states,
  backend-driven polling (no optimistic updates), per-step progress UX for 10–30s+ calls.
- **`frontend-design`** — invoke before building or reshaping a screen, for visual
  direction. `app-demo.html` is the floor, not the ceiling (spec §4.4) — match or beat it.
- **`web-design-guidelines`** — invoke after UI code exists, to audit accessibility and
  interaction behaviour.
- **`code-review-expert`** — run on a task's diff before marking it done in
  `docs/tasks.md`. Fix P0/P1 findings in-scope; P2/P3 optional or deferred to a new task
  line.

Where a design-skill suggestion conflicts with `frontend-rules`, the project rules win.

---

## 4. Reference material (read before coding)

- `docs/ASSESSMENT.md` — the full assessment text (source of truth for every requirement).
- `docs/app-demo.html` — reference UI. **Missing from the repo right now — add it before
  starting frontend work.** Click through it once; note the three gaps it leaves open
  (no error state, single-tab duplicate-click guard, unrealistic ~2s/8s timings) — do not
  port its `localStorage` store or its fake timings.
- `Book_illustration.ipynb` (Colab, Google Gemini cookbook) — run it yourself first.
- https://ai.google.dev/gemini-api/docs — REST reference.
- https://ai.google.dev/gemini-api/docs/rate-limits — check image-model limits before
  starting.
- `DECISIONS.md` — technical decision log (§2.6).
- `docs/tasks.md` — task breakdown and status (§2.4).
- `TESTING.md` — testing strategy + real test report (spec §5.4, required deliverable).

---

## 5. Deliverables checklist (spec §06 — do not ship without these)

- [ ] `README.md` — one start command, one test command, prerequisites, env vars,
      short architecture overview.
- [ ] `DECISIONS.md` — 4–6 decisions, free-prose format, ≥3 AI overrides, closing
      "one more day" answer.
- [ ] `TESTING.md` — FE+BE strategy plus a **real** test report (not invented).
- [ ] AI artifacts committed: this file, `.claude/`, `docs/tasks.md`, any saved prompts.
- [ ] `./start.sh` and `./test.sh` (or `make up`/`make test`) — one command each.
- [ ] `.env.example` — required env vars, no real secrets.
- [ ] Git history — small, incremental, real commit messages, committed as you go.

---

## 6. When stuck or something is ambiguous

Do not guess. In order:
1. Re-read `docs/ASSESSMENT.md` and `DECISIONS.md`.
2. Re-check the notebook and the REST docs (§2.3).
3. Still unclear → ask the user, with two options and a recommendation.

One question beats 200 lines written in the wrong direction.
