---
name: frontend-rules
description: Use when building or reviewing frontend screens for an AI pipeline app — covers the mandatory loading/error/empty states, polling pipeline status from the backend instead of optimistic updates, and per-step progress UX for long async operations.
---

# Frontend Rules

## 1. Every dynamic screen implements all three states

Any screen or component that reads remote data must handle **loading**, **error**, and
**empty** — not just the happy path. A screen missing one of them is incomplete and cannot
move to `review`.

| State | Requirement |
|---|---|
| **Loading** | A visible, layout-stable indicator. Use skeletons matching the final layout so content does not jump. Never render a blank screen while fetching. |
| **Error** | A human-readable message from `error.message` (never a raw code or stack) **plus a Retry button** that re-runs the same fetch. If `error.retriable` is false, explain what the user should do instead of offering Retry. |
| **Empty** | A distinct state for "loaded successfully, no data". It says what is missing and offers the primary action to fix it. Never show an empty list that looks identical to loading. |

Rules:
- Empty and error are **different** states. "Request failed" must never render as
  "no items yet".
- Every Retry button is idempotent from the UI's perspective: it re-issues the request and
  returns to the loading state; it never starts a second parallel request while one is
  in flight (disable it while pending).
- The three states are driven by the backend envelope's `status` field
  (`success | error | loading`, see `.claude/skills/backend-rules/SKILL.md` §1) — do not invent a
  parallel client-side state model.

## 2. Poll or subscribe to pipeline state — no fake optimistic updates

Pipeline state lives on the backend. The UI **reflects** it; it does not predict it.

- After triggering a step, poll (or subscribe to) the status endpoint and render whatever
  the backend reports. Do not mark a step "done" locally before the backend confirms.
- Do NOT optimistically flip a step to succeeded/completed. The API call costs money and
  can fail; a UI that lies about it makes users act on false state.
  - Acceptable optimistic feedback is limited to *intent*: disabling the button and showing
    "Starting…" while the trigger request is in flight.
- Polling rules:
  - Poll only while a run is active (`status = running` or `step_state` in
    `locked | calling`). Stop polling on `succeeded`, `failed`, `completed`.
  - Use a fixed, sane interval (start around 1.5–3s) and back off if the run is long.
    Never poll faster than the backend can answer.
  - Stop polling when the tab is hidden; resume on focus.
  - Always clear the timer/subscription on unmount — a leaked poller keeps hitting the API.
- On mount, **read state from the backend first**, before rendering any run controls. Two
  tabs and a refresh must all converge on the same picture.
- If the backend answers `STEP_LOCKED`, that is not an error banner — it means another
  tab/session is running the step. Show "already running" and keep polling.
- Never persist pipeline state in client storage as the source of truth. Client state is a
  cache of the backend response, nothing more.

## 3. Long async UX (10–30s+): per-step progress, not one generic spinner

Pipeline steps take tens of seconds. A single unlabeled spinner for that long reads as a
frozen app and drives users to refresh — which is exactly what the backend lock has to
defend against.

Requirements:
- Show the **full step list** with each step's state:
  `done ✓ / running… / pending / failed ✗`, so the user sees where they are in the whole
  pipeline (e.g. "Step 2 of 5").
- Label the running step with what it is actually doing ("Generating outline…"), not
  "Loading…".
- Show elapsed time on the running step. If it exceeds the expected duration, add a
  reassurance line ("This can take up to ~30s. Your progress is saved.").
- Keep the rest of the UI usable where it is safe to do so. Disable only the actions that
  genuinely conflict with the running step, not the entire page.
- On failure, keep the completed steps visible and mark only the failed one — the user must
  see that earlier work was not lost, and that Retry resumes rather than restarts.
- Never show a progress bar with invented percentages. Progress is derived from real step
  completion counts reported by the backend, or it is not shown as a bar at all.
- Reloading mid-run must land the user back on the same in-progress view (from backend
  state, per §2), never on a fresh empty form.

## Red flags

| Thought | Reality |
|---|---|
| "It'll load fast, skip the loading state" | A slow network or a 20s API call makes it a blank screen. |
| "Empty list is basically the same as loading" | Users cannot tell failure from emptiness. Separate them. |
| "Mark it done, it'll almost certainly succeed" | The paid API call may fail. Show only confirmed state. |
| "One spinner is enough" | For 30s it reads as frozen. Show per-step progress. |
| "Store run state in localStorage" | Two tabs diverge. The backend is the source of truth. |
| "Poll every 200ms so it feels instant" | That is a self-inflicted load test. Use a sane interval. |
