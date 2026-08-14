# Tasks — book-illustrator

Source of truth for status is GitHub Issues (repo `DuyNguyen-3006/book-illustrator`),
grouped into 8 milestones and tagged with labels `backend` / `frontend` / `docs` /
`setup` / `qa`. This file mirrors that as a flat checklist. See `CLAUDE.md` §2.4 for the
Plan → Test-first → Code → Review → Done flow every issue goes through.

Filter on GitHub: `gh issue list --repo DuyNguyen-3006/book-illustrator --label backend`
(or `frontend`, `docs`, `setup`, `qa`) — or use the Labels filter in the repo's Issues tab.

`[ ]` open · `[~]` in progress (max 1 at a time) · `[x]` closed

## Milestone 1 — 0. Groundwork (do first, before any app code)
- [ ] #1 `[SETUP]` Run `Book_illustration.ipynb` in Colab yourself, steps 1–5 only (note
      model IDs, request/response shape per step, context chaining, structured output)
- [ ] #2 `[SETUP]` Get `app-demo.html` from the assessment package into
      `docs/app-demo.html`; click through every screen and state
- [ ] #3 `[SETUP]` Get a Gemini API key; check free-tier rate limits for the image model
- [ ] #4 `[SETUP]` Fill `CLAUDE.md` §1 stack table's Gemini model rows; log the choice in
      `DECISIONS.md`

## Milestone 2 — 1. Backend skeleton
- [x] #5 `[BE]` Project scaffold (Java + Spring Boot, Clean Architecture layers),
      `docker-compose.yml` (app + Postgres), `backend/.env.example` +
      `frontend/.env.example` (separate per project, no shared root `.env`), `./start.sh`
- [x] #6 `[BE]` Storage layer: Postgres schema (users, projects, pipeline
      status/step_state, characters, chapters) + migration tool; locking via
      `SELECT ... FOR UPDATE` or unique constraint on `(project_id, step_id)`
- [x] #7 `[BE]` Identity: email + name → find-or-create user, session cookie (no
      password/OAuth)

## Milestone 3 — 2. Projects
- [x] #8 `[BE]` Create project: title + book text (paste or `.txt` upload), validation
- [x] #9 `[BE]` List projects for a user: title, created date, status, per-step progress
- [x] #10 `[BE]` Project detail read model: current step, all step outputs so far, book
      text

## Milestone 4 — 3. Pipeline state machine and concurrency (before real Gemini calls)
- [x] #11 `[BE]` Implement `status`/`step_state` model per
      `.claude/skills/pipeline-rules/SKILL.md` §1
- [x] #12 `[BE]` Per-`(project_id, step_id)` lock with TTL, per pipeline-rules §2
      (unit-test: two concurrent triggers → exactly one call) — already satisfied by #6
- [x] #13 `[BE]` Resume logic per pipeline-rules §3 (unit-test: kill mid-step, reload,
      resumes correctly)

## Milestone 5 — 4. Pipeline steps (wire real Gemini calls one at a time, §2.3 research rule)
- [x] #14 `[BE]` Step 1 — Style (user-provided or generated from text)
- [ ] #15 `[BE]` Step 2 — Characters, structured JSON, **max 2**, adults only
- [ ] #16 `[BE]` Step 3 — Portraits, one image per character
- [ ] #17 `[BE]` Step 4 — Chapters, structured JSON, **max 1**, referencing characters
- [ ] #18 `[BE]` Step 5 — Illustrations, one per chapter, reusing portraits for
      consistency
- [ ] #19 `[BE]` Context chaining: book text sent once, not resent per step; fill in
      `pipeline-rules SKILL.md` §5 once known

## Milestone 6 — 5. Frontend
- [ ] #20 `[FE]` Identity screen (name + email, validation)
- [ ] #21 `[FE]` Project list: status pill, 5-step progress indicator, empty state
- [ ] #22 `[FE]` New project: title, paste-or-upload `.txt`, validation
- [ ] #23 `[FE]` Project detail: stepper, book text view, style, character cards,
      chapter cards, action button, per-item portrait/illustration progress
- [ ] #24 `[FE]` In-progress state naming the running step; error state + retry button;
      stuck-step recovery affordance
- [ ] #25 `[FE]` Sign out

## Milestone 7 — 6. Tests and docs
- [ ] #26 `[BE]` Backend tests: step ordering, progress, retry (per backend-rules
      skill §3)
- [ ] #27 `[FE]` Frontend tests: a couple of components' loading/error/empty states
- [ ] #28 `[DOCS]` `TESTING.md`: strategy + a real test-run report
- [ ] #29 `[DOCS]` `README.md`: start/test commands, prerequisites, env vars,
      architecture overview
- [ ] #30 `[SETUP]` `./test.sh`

## Milestone 8 — 7. Final pass
- [ ] #31 `[QA]` Manually test: refresh mid-step, second tab, double-click, forced
      stuck step, server restart mid-pipeline
- [ ] #32 `[FE]` Re-check against `docs/app-demo.html` — cover everything it does
- [ ] #33 `[DOCS]` `DECISIONS.md` has ≥4 entries, ≥3 AI overrides, closing "one more
      day" answer
- [ ] #34 `[QA]` Squash nothing — verify commit history reads as incremental, real
      timestamps

---

## Quick counts
- `[BE]` 16 · `[FE]` 8 · `[DOCS]` 3 · `[SETUP]` 5 · `[QA]` 2 (34 total)
