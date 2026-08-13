---
name: pipeline-rules
description: Use when designing, implementing, or debugging a multi-step AI pipeline (Gemini API) — covers state machine separation, per-step locking against duplicate calls, and resume-after-crash behaviour.
---

# Pipeline Rules

Rules for a multi-step pipeline where each step calls an external AI API (Gemini). The
pipeline must be **resumable**, **duplicate-safe**, and driven by an **explicit state
machine**.

## 1. State machine: `status` and `step_state` are separate fields

Never encode both project progress and step progress in a single column.

| Field | Level | Answers |
|---|---|---|
| `status` | project / run | "Is this whole run idle, running, blocked, finished?" |
| `step_state` | current step | "What is happening inside the step right now?" |

Baseline values (extend per project, but keep the two axes separate):

```
status      : draft | running | paused | failed | completed
step_state  : pending | locked | calling | succeeded | failed
current_step: <int or step key>  ← which step the run is on
```

Rules:
- `status` changes only on run-level events (user starts, run finishes, run is abandoned).
- `step_state` changes only within one step and resets to `pending` when the run advances
  to the next step.
- Every transition is written to storage **before** the side effect it authorizes, never
  after. Order is always: persist state → perform action → persist result.
- Illegal transitions must raise, not be silently corrected. A run in `completed` cannot
  go back to `calling`.
- Derive UI labels from `(status, current_step, step_state)`. Do not add a third,
  denormalized "display status" field that can drift.
- Store `last_error` and `updated_at` alongside the state — resume and debugging both
  depend on them.

## 2. Locking: acquire a per-(project, step) lock before any API call

Duplicate calls are the default failure mode here: a browser refresh, a double-click, two
open tabs, or a re-mounted component all trigger the same step again. The API call costs
money and time, so the lock is not optional.

Required behaviour:
- The lock key is `(project_id, step_id)` — never a global lock, never per-user.
- Acquisition is **atomic** at the storage layer. Acceptable mechanisms: a unique
  constraint insert, `SELECT ... FOR UPDATE`, a conditional `UPDATE ... WHERE
  step_state = 'pending'` that must affect exactly 1 row, or a DB advisory lock.
  A read-then-write check in application code is NOT acceptable — it races.
- Sequence for every step execution:
  1. Try to acquire the lock (`pending` → `locked`). Failed acquisition = someone else is
     already running this step.
  2. On failure, return the **current state** to the caller — do not queue, do not wait,
     do not start a second call. The frontend shows "already running" and keeps polling.
  3. Set `step_state = 'calling'`, persist, then call the API.
  4. Persist the result and set `succeeded` / `failed`, releasing the lock in the same
     transaction as the result write.
- The lock carries a **TTL / heartbeat** so a crashed worker does not block the step
  forever. Choose the TTL to exceed the worst-case API latency of the step and record the
  choice in `DECISIONS.md`.
- Reclaiming an expired lock is a state transition too — log it explicitly, never silently.
- If the step writes results, use an idempotency key derived from `(project_id, step_id,
  attempt)` so a late response from a reclaimed lock cannot double-write.

## 3. Resume: always continue from the interrupted step

After a crash, a refresh, or the user returning hours later, the run continues from where
it stopped. Re-running completed steps is a bug, not a safe default.

- On load, read `(status, current_step, step_state)` and branch:
  - `succeeded` → advance to the next step.
  - `pending` → the step has not started; it may be started.
  - `locked` / `calling` with a live lock → do nothing, report "in progress", poll.
  - `locked` / `calling` with an expired lock → the previous attempt died; reclaim and
    re-run **only that step**.
  - `failed` → surface the error; the user decides whether to re-run that step.
- Output of every completed step is persisted before the run advances, so a resumed run
  never needs to recompute an earlier step to obtain its input.
- Never implement "start over from step 1" as the recovery path. If a full restart is ever
  needed it must be an explicit, separate user action with its own confirmation.
- Resume logic must be covered by tests that kill the process mid-step (see
  `skills/backend-rules/SKILL.md` §3).

## 4. Per-step item limits

| Step | Item limit | Source of the limit |
|---|---|---|
| Characters | max 2 | Assessment spec §03 (Reference Pipeline table) — hard requirement, not the notebook's default |
| Chapters | max 1 | Assessment spec §03 (Reference Pipeline table) — hard requirement, not the notebook's default |
| Portraits | 1 per character (≤2 total) | Derived: one portrait per character generated |
| Illustrations | 1 per chapter (≤1 total, per current limit) | Derived: one illustration per chapter generated |

The limit is enforced server-side and validated before the API call, not only in the UI.
Exceeding it is a validation error, not a truncation.

## 5. Context chaining instead of resending the full input

Confirmed from a real run of `Book_illustration.ipynb` (steps 1–5, Python SDK
`google-genai>=2.10.0`). REST/JS mapping still needs a final check against
https://ai.google.dev/gemini-api/docs before coding (§2.3) — the shapes below are the
*mechanism*, not yet verified wire format.

- **Mechanism used:** the "interactions" API — `client.interactions.create(...)`. Two
  separate chains run in parallel, not one:
  1. **Book upload, once:** `client.files.upload(file="book.txt")` → `book.uri`. Sent
     in the very first interaction only.
  2. **Text/prompt chain** (style → characters → chapters, all on `GEMINI_MODEL_ID`):
     each call passes `previous_interaction_id=<prior interaction>.id`. The book is
     never resent — the chain remembers it from the first turn that included
     `{"type": "document", "uri": book.uri}`.
  3. **Image chain** (portraits, on `IMAGE_MODEL_ID`): a *separate* interaction chain,
     seeded with the style + negative-prompt instructions, one call per character with
     `previous_interaction_id` pointing at the prior image call.
- **What is passed forward between steps:** the `id` of the previous interaction
  (`previous_interaction_id`). That's the only handle needed — persist one
  `last_text_interaction_id` and one `last_image_interaction_id` per project in our
  JSON storage (see `status`/`step_state` in §1), not the interaction content itself.
- **What is stored locally vs. provider side:** the book text, uploaded file, and full
  conversation history live on Google's side, addressed by `book.uri` and interaction
  `id`s. We store only the ids/uris plus the structured results we already need
  (style text, character `{name, prompt}` list, chapter `{name, prompt, characters}`
  list, generated image bytes) — this doubles as our resume state, not just a cache.
- **Chapters must use the extended schema, not the notebook's simple default.** The
  notebook shows two variants: a bare `{name, prompt}` schema (its default, cell 38) and
  an extended one adding `characters: list[str]` (its "Bonus: more granular control"
  section, cell 41–42). Assessment spec §03 requires chapter prompts to *reference the
  characters* — use the extended schema; the bare one loses that field entirely.
- **Illustrations (step 5) — use the granular per-chapter approach, not the long chain.**
  The notebook shows two ways to generate chapter illustrations:
  - *Simple:* keep extending the image chain and hope the model "remembers" earlier
    portraits from conversation history.
  - *Granular (Bonus section):* look up only the character images that specific chapter
    needs (`chapter['characters']`), decode their stored bytes, and pass them directly
    as image parts in a **fresh, standalone** `interactions.create` call (no
    `previous_interaction_id`) alongside the chapter prompt.
  Use the granular approach. It fits our architecture: each illustration call becomes
  self-contained (chapter prompt text + that chapter's specific portrait bytes, both
  already in our storage from steps 2–4) instead of depending on one long-lived chain
  staying intact across a resumable, potentially-interrupted pipeline. It also matches
  "reusing the portraits so characters stay consistent" (spec §03) literally rather than
  hoping the model recalls them.
- **Fallback when unavailable:** none needed — File API upload + interaction chaining is
  the primary mechanism for every step in scope (1–5); no step lacks it.

**Auto-retry conflict — must override the notebook's default (log in `DECISIONS.md`):**
the notebook's client is configured with
`http_options.retry_options=HttpRetryOptions(attempts=5, initial_delay=2.0, max_delay=60.0,
http_status_codes=[429,500,502,503,504])` — i.e. it auto-retries 5x on exactly the
status codes `CLAUDE.md` §2.2 forbids auto-retrying on. Our backend must set this to a
single attempt (or the equivalent no-retry option in whatever SDK/REST client we end up
using) — a failed call surfaces as `error.retriable=true` and the *user* retries via the
existing per-step lock (`pipeline-rules` §2), never the HTTP client.

## Red flags

Stop if you catch yourself doing any of these:

| Thought | Reality |
|---|---|
| "One status field is enough" | Run-level and step-level progress diverge. Two fields. |
| "I'll check if it's running, then start it" | That is a race. Use an atomic acquire. |
| "Simplest fix is to re-run the pipeline" | That is a duplicate paid API call. Resume instead. |
| "The lock can be in memory" | Multiple workers / restarts lose it. Persist it. |
| "I'll just resend the whole input" | Token cost grows with every step. Chain context. |
| "The UI already prevents double-clicks" | The UI is not a concurrency control. Lock server-side. |
