---
name: backend-rules
description: Use when writing or reviewing backend code for an AI pipeline app — covers the internal BE-FE response contract, error handling around external API calls, and the test requirement for pipeline/state-machine logic.
---

# Backend Rules

## 1. Internal BE-FE API contract

Every endpoint returns the **same envelope**. The frontend must never have to special-case
the shape of a response per route.

```jsonc
// success
{
  "status": "success",
  "data":  { /* payload, route-specific */ },
  "error": null
}

// error
{
  "status": "error",
  "data":  null,
  "error": {
    "code":    "STEP_LOCKED",          // stable, machine-readable, SCREAMING_SNAKE
    "message": "This step is already running.",  // safe to show to the user
    "details": { "step_id": 2 },       // optional, structured, never a raw stack trace
    "retriable": true                  // may the user re-trigger this action?
  }
}

// loading / in-progress (long-running pipeline steps)
{
  "status": "loading",
  "data":  { "current_step": 2, "step_state": "calling", "progress": 0.4 },
  "error": null
}
```

Rules:
- `status` is exactly one of `success | error | loading`. No other values, no `null`.
- `data` and `error` are mutually exclusive; the unused one is explicitly `null`.
- A long-running step returns `loading` with enough state for the UI to render per-step
  progress (see `skills/frontend-rules/SKILL.md` §3) — not just a boolean.
- HTTP status codes stay meaningful (4xx for client errors, 5xx for server faults) **and**
  the body still carries the envelope. The frontend reads the body, not only the code.
- `error.code` values are a closed, documented set. Adding a code is a contract change:
  update the shared type/schema and the frontend in the same task.
- Never return an empty `200 OK` body. An action with no payload returns
  `{"status":"success","data":{},"error":null}`.

## 2. Error handling around external API calls

Every call to the Gemini API (or any external service) is wrapped in explicit error
handling. No bare calls, no blanket top-level handler as the only defense.

Required:
- A `try` / `catch` (or equivalent) around **each** external call site, catching a narrow
  set of expected failures plus a final catch-all.
- Log enough to debug without opening a debugger:
  `project_id`, `step_id`, `attempt/idempotency key`, request metadata (model, parameter
  summary, payload **size** — not the payload), latency, provider status code, provider
  error code/message, and a correlation id shared with the frontend response.
- Log the correlation id in the error response so a user-reported failure maps to a log
  line: `error.details.trace_id`.
- **Never leak raw errors to the frontend.** No stack traces, no provider payloads, no
  API keys, no internal hostnames, no SQL. Map to a stable `error.code` plus a
  human-readable `message` written for the end user.

```
provider 429   → RATE_LIMITED     "The AI service is busy. Try again in a moment."
provider 400   → INVALID_INPUT    "The input for this step was rejected."
provider 5xx   → UPSTREAM_ERROR   "The AI service failed. Your progress is saved."
timeout        → UPSTREAM_TIMEOUT "The step took too long and was stopped."
lock held      → STEP_LOCKED      "This step is already running."
```

- On failure, persist the failed state (`step_state = 'failed'`, `last_error`) **before**
  returning, so a resumed session sees the same truth as the response did.
- Do NOT auto-retry (see `CLAUDE.md` §2.2). A failed call surfaces as a `retriable` error
  and the user decides.
- Unknown/unmapped exceptions become `INTERNAL_ERROR` with a generic message — logged in
  full, exposed as nothing more than a trace id.

## 3. Tests: pipeline and state-machine logic must be tested before a task is "done"

A task touching pipeline or state-machine logic cannot move to `review` without tests.

Minimum coverage:
- **Legal transitions** — each allowed `status` / `step_state` transition succeeds.
- **Illegal transitions** — each forbidden transition raises and leaves state unchanged.
- **Lock behaviour** — two concurrent attempts on the same `(project_id, step_id)`:
  exactly one acquires, the other gets `STEP_LOCKED`, and exactly one API call is made.
- **Resume** — a run interrupted mid-step (state `calling`, lock expired) resumes at that
  step, does not re-run earlier steps, and does not duplicate output.
- **Error mapping** — each provider failure mode maps to the documented `error.code`, and
  no raw provider text appears in the response body.
- **Idempotency** — a late response from a reclaimed attempt does not double-write results.

Test rules:
- The external API is **stubbed** in tests. No real Gemini calls in the test suite, ever.
- The stub asserts **call counts**, not only outputs — duplicate-call prevention is only
  provable by counting.
- Tests use real storage semantics for anything concurrency-related. An in-memory fake
  that cannot express atomic acquire proves nothing about the lock.
- A failing test is fixed, never skipped or deleted to make the suite pass.

## Red flags

| Thought | Reality |
|---|---|
| "This endpoint is simple, plain JSON is fine" | The FE now needs a special case. Use the envelope. |
| "Return the provider's error, it's clearer" | It leaks internals. Map it to a code. |
| "Global exception handler covers everything" | It cannot log step context. Handle at the call site. |
| "I'll add tests after the demo" | Then the task is not done. Tests come before `review`. |
| "The mock returns the right value, so it works" | Assert call count too, or duplicates go unnoticed. |
