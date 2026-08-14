# Book Illustrator

Turns a book's text into character portraits and a chapter illustration through the Gemini
API, in five steps the user runs one at a time: **Style → Characters → Portraits → Chapters
→ Illustrations**.

Nothing runs on its own, every result is saved as it lands, and a project can be closed and
reopened at any point without losing work or paying for the same step twice.

---

## Prerequisites

- **Docker** with Compose. That is the whole list: the backend builds and tests inside
  containers, so no local JDK, Maven or Node is required.
- **A Gemini API key** of your own: <https://ai.google.dev/gemini-api/docs>

> **Note on image generation.** The Portraits and Illustrations steps call an image model.
> On a free-tier key the image quota is zero (every Nano Banana model answers `429`, and the
> Imagen models answer `404 no longer available to new users`), so those two steps cannot
> complete without billing enabled on the Google account. The three text steps work on the
> free tier. See `TESTING.md` for exactly what was verified live and what was not.

## Start it

```bash
./start.sh
```

The script creates `backend/.env` and `frontend/.env` from their examples on first run, then
brings up Postgres, the API and the UI.

**Put your key in `backend/.env` (`GEMINI_API_KEY=...`) before running a pipeline step.**

Then open <http://localhost:5173>. Sign in with any name and email: there is no password,
an unknown email creates an account, and a known one loads its projects.

## Test it

```bash
./test.sh
```

Backend suite (real Postgres, stubbed Gemini) followed by the frontend suite. Strategy, the
manual QA scenarios and a real run's output are in [`TESTING.md`](TESTING.md).

## Environment variables

| Variable | Where | Purpose |
|---|---|---|
| `GEMINI_API_KEY` | `backend/.env` | Your own key. Never committed; `.env` is git-ignored. |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `backend/.env` | Database credentials, also read by the Postgres container. |
| `PORT` | `backend/.env` | API port, default `8080`. |
| `STORAGE_ROOT` | `backend/.env` | Where book text and generated images are written. Compose sets it to `/data` so they survive a rebuild. |
| `VITE_API_BASE_URL` | `frontend/.env` | Only used by the Vite dev server's proxy target; the container talks to the API through nginx. |

## Architecture

```
browser ──> nginx (frontend container, port 5173)
              ├── serves the built React app
              └── proxies /api/ ──> Spring Boot (app container, port 8080) ──> Postgres
                                          └──> Gemini REST API
                                          └──> local filesystem (book text, images)
```

The browser only ever talks to one origin, so the session cookie is first-party and the
backend carries no CORS configuration. In development the Vite proxy plays nginx's part.

**Backend** — Java + Spring Boot, Clean Architecture:

```
interfaces/rest → application (usecase, port/output) → domain (entity, enums, exception)
infrastructure  → implements the output ports (Postgres, Gemini REST, local files)
```

`domain` imports no Spring; controllers reach infrastructure only through use cases.
`ArchitectureTest` (ArchUnit) fails the build if that stops being true. Full rules:
[`docs/architecture.md`](docs/architecture.md).

**Frontend** — React + Vite + TypeScript, feature-sliced:

```
src/features/<domain>/{components,pages,hooks,services,types}
src/shared/{components,lib,types}   src/router   src/config   src/components/ui
```

TanStack Query owns server state, including polling a running step and stopping when it
finishes. One module knows the response envelope; screens never branch on the shape of a
response.

**Storage** — Postgres holds users, projects, pipeline state, characters and chapters. Book
text and generated images are files on a mounted volume; the database stores their paths.

### How the pipeline behaves

- **User-driven and ordered.** A step runs only when asked, and the backend picks which step
  that is, so a stale tab cannot skip ahead.
- **No duplicate calls.** Acquiring a step is a conditional `UPDATE`, so a double click, a
  refresh or a second tab loses the race and is shown the run already in flight instead of
  starting a second one.
- **Resumable.** Progress lives in the database. Reopening a project shows its true state,
  including a step still running and how long it has been running.
- **Retryable.** A failed step keeps everything generated before it, records why it failed,
  and can be retried on its own.
- **Never stuck.** A run that holds a step past the 120s lock TTL can be reclaimed by the
  next attempt from the UI. No database surgery.
- **Never auto-retried.** A failed Gemini call is surfaced and left to the user, per the
  assessment's cost rule.

## Repository map

| Path | What |
|---|---|
| `backend/` | Spring Boot service, Flyway migrations, tests |
| `frontend/` | React app, nginx config, tests |
| `docs/architecture.md` | Backend architecture, written before the code followed it |
| `docs/tasks.md` | The task list, mirroring GitHub Issues |
| `DECISIONS.md` | Why things are the way they are, including where I overrode the AI |
| `TESTING.md` | Test strategy, manual QA, real run output |
| `CLAUDE.md`, `.claude/`, `.agents/` | The AI working contract and its skills |

## Known limitations

- Image steps need a billed Google account (above).
- A backend restart clears sessions, since they are in-memory; pipeline state is unaffected
  and the UI sends the user back to sign in.
- Local only, by the assessment's instruction. Do not deploy this publicly with a real key.
