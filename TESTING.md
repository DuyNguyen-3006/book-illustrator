# TESTING.md — book-illustrator

One command runs everything:

```bash
./test.sh
```

It starts Postgres, runs the backend suite in a `maven:3.9-eclipse-temurin-21` container on
the compose network, then the frontend suite in `node:22-alpine`. Docker is the toolchain,
so no local JDK 21, Maven or Node is needed. Report from a real run is at the bottom.

---

## What I test, and why that

The pipeline's value is in its rules, not in its CRUD. So the tests concentrate on the
places where being wrong is expensive: spending money twice at Gemini, losing generated
work, or leaving a project stuck.

### Backend (133 tests)

**The state machine** (`ProjectTest`, `ProjectResumeTest`). Every legal transition, and
every illegal one, asserted to raise and leave the state untouched. Resume is tested as a
decision function: given `stepState` and how long the step has been running, what should a
reopened session do — start, wait, reclaim, or surface an error.

**The lock, against a real Postgres** (`PostgresPipelineLockTest`, `AuthControllerTest`,
`ProjectControllerTest`). Two concurrent acquisitions of the same step: exactly one wins.
An in-memory fake cannot express an atomic conditional `UPDATE`, so proving the lock
against one would prove nothing. These tests need the docker-compose database, which is
why `test.sh` starts it first.

**Duplicate calls, counted rather than assumed** (`RunStyleStepUseCaseTest` and the other
three step use cases). The Gemini gateway is stubbed and the assertions count invocations.
A test that only checks the output would pass while the app quietly paid twice.

**Error mapping** (all four step use cases). Each provider failure mode maps to its
documented code: 429 to `RATE_LIMITED`, 4xx to `INVALID_INPUT`, 5xx to `UPSTREAM_ERROR`,
timeout to `UPSTREAM_TIMEOUT`. The tests also assert the failure is persisted before the
response is returned, so a resumed session sees the same truth the caller did.

**The caps** (`RunCharactersStepUseCaseTest`, `RunChaptersStepUseCaseTest`). More than 2
characters is rejected as `INVALID_OUTPUT`, and — added after manual QA caught it — a
rerun replaces what it wrote last time rather than appending. See the QA section.

**The layering** (`ArchitectureTest`, ArchUnit). `domain` may not import Spring, controllers
may not touch infrastructure. Prose in a document does not stop that; a failing build does.

### Frontend (46 tests)

**The three states, per screen** (`ProjectListPage`, `ProjectDetailPage`, `IdentityForm`,
`NewProjectForm`). Loading renders a skeleton shaped like the real content, empty is
distinct from failed and carries the action that fixes it, and errors show the backend's own
message with a retry that disables while in flight.

**Live pipeline behaviour** (`ProjectDetailStates`). Polling starts only while a step is
running and stops on its own; an idle project is never polled; `STEP_LOCKED` renders as
"already running somewhere else" rather than as a failure; a run that outlived the lock TTL
offers a way out.

**The rules underneath** (`projectProgress`, `stepRun`). Pure functions, tested at the
boundaries: a step is not stuck at exactly the TTL, only past it.

**Validation that mirrors the server** (`NewProjectForm`). Exactly one of pasted text or a
`.txt` upload, matching `resolveBookText` on the backend, so the user gets a sentence
instead of a 400.

## What I deliberately do not test

- **Real Gemini calls.** Never in the suite: they cost money, they are slow, and they are
  flaky in ways that would make a red build meaningless. The wire format is verified by
  hand instead — see "live verification" below.
- **End-to-end browser tests.** The assessment says E2E is not expected. The five scenarios
  in the QA section were run by hand against the real stack, which for this scope buys more
  than a Playwright suite would.
- **Rendering details.** No snapshot tests. They fail on every visual change and catch
  nothing that matters here.
- **Framework behaviour.** No tests that Spring Data saves a row or that React Router
  routes; those test the library.

## Live verification, because passing tests are not proof of a wire format

Mocked tests prove the classification and state logic. They cannot prove the request shape
is what Gemini actually accepts. Each step therefore also got one real call, and that is
where the interesting bugs were:

- The Files API returns `mimeType`, not `mime_type`, and the interactions reply has no
  top-level `output_text` — the text is nested in `steps[].content[]`. Both contradicted
  the documentation and were only found by running it (#14).
- The image request was rejected twice: `image/png` is not accepted for
  `response_format.mime_type` (only `image/jpeg`), and the content array the text path
  sends is read as a turn_list where this model wants a step_list (#16).

**Known gap, stated plainly:** image generation has never completed against this key. Every
image model available to it answers `429` (the account's image quota is zero on the free
tier) and the Imagen models answer `404 no longer available to new users`. The Portraits
and Illustrations steps are therefore covered by unit tests and by the request shape being
validated by the API, but the *response* parsing has not been confirmed live. #16 and #18
stay open for that reason rather than being marked done.

## Manual QA against the running stack

Run with `./start.sh` up, using the API through the same nginx proxy the browser uses.

| Scenario | Result |
|---|---|
| Two concurrent `run-step` calls (double click) | Both 200, but only one ran. The other returned the envelope's `loading` status with `stepState: RUNNING` rather than firing a second paid call. |
| Refresh mid-step | The project reads back `CHARACTERS / RUNNING` with its real `stepStartedAt`, which is what the UI's elapsed timer uses. |
| Second tab during a run | Sees the same in-flight state; pressing the button there also returns `loading / RUNNING`. |
| Step stranded past the 120s lock TTL | The retry reclaimed the step with no database surgery. **It also found a bug** (below). |
| Backend restart mid-pipeline | Pipeline state survives: still `CHARACTERS / RUNNING` with its timestamp. |

**The bug that scenario 4 found.** A project with 2 characters, forced into the stuck state
and retried, came back with 4:

```
characters before: 2
retry: HTTP 200
characters after:  4 | Mole, Rat, Mole, Rat
```

A step that can legitimately run more than once was appending its output. The 2-character
cap is a hard, server-side requirement, so this was a correctness bug. Fixed in #43 by
making the write a replace inside the existing transaction; re-running the same scenario
now ends at 2. A regression test went in first.

**Known limitation, not a bug I hid.** The restart drops the session: `HttpSession` is
in-memory, so after the backend restarts the API answers 401 and the user signs in again.
Pipeline state is unaffected. The frontend handles it (the guard redirects to `/signin`).
Fixing it properly means persisting sessions, which is more machinery than this scope
justifies.

---

## Test report — real run, 2026-08-15

```
$ ./test.sh
==> Starting Postgres
 Container book-illustrator-postgres-1  Running
==> Backend tests
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 -- in com.bookillustrator.domain.entity.ProjectTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- in ...pipeline.RunStyleStepUseCaseTest
[INFO] Tests run:  7, Failures: 0, Errors: 0, Skipped: 0 -- in ...pipeline.RunCharactersStepUseCaseTest
[INFO] Tests run:  8, Failures: 0, Errors: 0, Skipped: 0 -- in ...pipeline.RunChaptersStepUseCaseTest
[INFO] Tests run:  7, Failures: 0, Errors: 0, Skipped: 0 -- in ...pipeline.RunPortraitsStepUseCaseTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- in ...gemini.GeminiGatewayAdapterTest
[INFO] Tests run:  3, Failures: 0, Errors: 0, Skipped: 0 -- in ...persistence.PostgresPipelineLockTest
[INFO] Tests run:  4, Failures: 0, Errors: 0, Skipped: 0 -- in ...persistence.ProjectResumeTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- in ...rest.controller.AuthControllerTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0 -- in ...rest.controller.ProjectControllerTest
[INFO] Tests run:  3, Failures: 0, Errors: 0, Skipped: 0 -- in com.bookillustrator.ArchitectureTest
[INFO]
[INFO] Results:
[INFO] Tests run: 133, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  44.309 s

==> Frontend tests
 ✓ src/shared/lib/projectProgress.test.ts (5 tests)
 ✓ src/features/pipeline/lib/stepRun.test.ts (8 tests)
 ✓ src/features/auth/components/IdentityForm.test.tsx (4 tests) 455ms
 ✓ src/features/auth/components/SignOutButton.test.tsx (2 tests)
 ✓ src/features/landing/pages/LandingPage.test.tsx (3 tests)
 ✓ src/router/ProtectedRoute.test.tsx (2 tests)
 ✓ src/features/projects/pages/ProjectListPage.test.tsx (4 tests) 227ms
 ✓ src/features/projects/components/NewProjectForm.test.tsx (7 tests) 728ms
 ✓ src/features/pipeline/pages/ProjectDetailPage.test.tsx (6 tests) 301ms
 ✓ src/features/pipeline/pages/ProjectDetailStates.test.tsx (5 tests) 5929ms
     ✓ names the running step and keeps polling until the backend says it finished  2622ms
     ✓ does not poll a project that is sitting idle  3216ms

 Test Files  10 passed (10)
      Tests  46 passed (46)
   Duration  9.17s

==> Both suites passed
```

The two slow frontend tests are slow on purpose: one waits for a poll to pick up a finished
step, the other waits long enough to prove an idle project is *not* polled.
