# DECISIONS.md — book-illustrator

Decisions only. Not a worklog, not a diary of what happened when — git history covers
that. A heading per decision, then a short paragraph in your own words: who proposed it,
who pushed back, where you landed, what it cost you. **No template to fill in.** Write the
entry the moment the decision is made, not batched at the end — vague or obviously
back-filled entries score badly (spec §2.1).

4–6 real decisions is plenty. At least 3 of them should be places you overrode the AI —
where its output was wrong, unsafe, or overcomplicated, and what you did instead (spec
§2.3). The push-back goes both ways: some entries should be the AI catching *your*
mistake, not just the reverse.

Required coverage (spec §06) — make sure these three are in here somewhere:
- Stack and storage choice (see the proposed table in `CLAUDE.md` §1 — confirm, change,
  or replace it, and say why).
- How pipeline progress is modeled (`status` vs `step_state` — see
  `.claude/skills/pipeline-rules/SKILL.md` §1).
- How duplicate execution on refresh/second-tab is prevented (locking — see
  `.claude/skills/pipeline-rules/SKILL.md` §2).

Example of the expected shape (from the assessment brief itself):

> ## Separate `status` and `step_state`
>
> Claude proposed a single `status` enum. I pushed back — one enum can't express "step 3
> done, step 4 currently running", which is exactly the state a refresh mid-step has to
> read correctly. Split it in two. Cost: two fields to keep in sync, and a stranded
> `step_state` needs a timeout to clear.

---

# Decisions

<!-- DRAFT below — written by Claude from the actual conversation, not invented. Read it,
     rewrite it in your own voice/details before this counts as a real entry. -->

## Locking: a conditional UPDATE, not a held SELECT ... FOR UPDATE

For the duplicate-call guard (spec §4.3 — refresh/second-tab/double-click must not fire
Gemini twice), the obvious-sounding option is `SELECT ... FOR UPDATE` around the whole
step. I almost did that. Claude flagged the actual problem: that holds a Postgres
transaction — and a pooled connection — open for the entire 10-30s+ Gemini call, which is
a bad trade for a lock that only needs to be held for a status flip. Went with a single
atomic `UPDATE projects SET step_state='locked' WHERE ... AND step_state='pending'`
instead — acquire and release are instant, the row's `step_state` itself carries the lock
across the actual API call. Same conditional-UPDATE mechanism also handles reclaiming an
expired lock (TTL 120s — chosen to exceed the slowest expected single Gemini call, per
`pipeline-rules` §2) in the same statement. Cost: the lock logic lives entirely in one raw
SQL statement instead of ORM-managed locking, so it needs its own concurrency test rather
than trusting an annotation.

## Testing the lock against the real docker-compose Postgres, not Testcontainers

Claude's recommendation was Testcontainers — spins up an isolated Postgres per test run,
no manual setup, works in CI. I went the other way: point the test at the same Postgres
`docker-compose` already starts, since I'm running this by hand anyway and didn't want a
second test-only Postgres dependency on top of the real one. Cost, which I'm accepting
knowingly: `mvn test` now silently fails if `docker compose up` isn't running first — no
CI safety net, and there's a small risk of the test's cleanup step leaving rows behind if
a test crashes mid-run. Verified manually after the first run that cleanup left 0 rows.

## Backend: Java instead of Node.js

The initial setup (done with Claude, before I'd read the assessment stack rule closely)
picked Node.js + Express — "boring and familiar" per spec §5.1, taken at face value as
"whatever's conventional." I pushed back: Java is what I actually move fast in, and
"familiar" in the spec means familiar *to me*, not a specific stack. Switched to Java +
Spring Boot. Cost: the Gemini "interactions" API (the chat-chaining mechanism the
reference notebook uses for style→characters→chapters) only ships Python/JS SDKs — Java
has to call it over REST directly, no convenience client. Found this out from re-reading
the assessment's own hint about SDK coverage, not by guessing.

## Storage: PostgreSQL instead of JSON files

Also part of the same stack conversation. JSON files were the original pick (isolated
per-project directory, hand-rolled write lock) — spec §5.2 explicitly allows this "if done
properly." Went with Postgres instead: real transactions and row locking
(`SELECT ... FOR UPDATE` / a unique constraint on `(project_id, step_id)`) instead of
building lock/TTL/stale-reclaim logic by hand on top of the filesystem. Cost: one more
moving part to run locally — mitigated by shipping it in `docker-compose.yml` so
`./start.sh` still stays one command, and it's still fully local/offline, no hosted DB
account needed to grade this.

## Architecture: Clean Architecture — and the over-engineering risk I'm accepting

My call, not Claude's suggestion. Claude flagged the trade-off rather than agreeing or
refusing: at this scope (5 pipeline steps, 4 screens, solo, ~16h), layering everything
into entities/use-cases/adapters/frameworks risks being exactly the "AI will hand you more
structure than this needs" the spec warns about in §05 — and "right-sized solution" is a
graded criterion (§07), separate from whether the app works. The concrete risk: an
interface with one implementation and no second one planned, or a "use case" that's just a
pass-through to a repository, is ceremony, not architecture. The reason I'm keeping it
anyway: the state machine (`status`/`step_state`, locking, resume) is the part that has to
be unit-tested in isolation per `backend-rules` §3, and keeping that logic out of
Spring/JPA/HTTP makes those tests fast and dependency-free. If I notice mid-build that a
layer boundary isn't earning its keep, I'll collapse it rather than defend it for
consistency.

---

## If you had one more day, what would you build and why?

> Answer honestly, briefly, in priority order — deliberately skipped work, not a feature
> wishlist.

1.
2.
3.
