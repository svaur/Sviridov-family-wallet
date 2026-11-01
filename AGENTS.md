# Project conventions (for AI agents)

## Git workflow

- **Never commit without explicit user approval.** Prepare changes in the working
  tree (staged or unstaged), report what was changed, and wait for the user to
  verify correctness before committing.
- **The user commits and pushes themselves.** Do not run `git commit` or `git push`.

## Stage gates

- Proceed to the next implementation stage only after explicit user consent
  (e.g. "start stage N"). After finishing a stage, pause and wait for the "go".

## Security

- Secrets only via env / docker secrets; never commit real passwords or PII.
- Test PDFs must be sanitized (no real personal data).
- Before committing, check the changed data for personal data, API keys and other
  sensitive information. If any is found — stop the commit and get my explicit
  approval to commit.

## Categorization

- Auto-application of rules and the manual "apply rules" button must NOT overwrite
  a manually assigned category. Manual (explicit) category assignment is protected
  from rules (implemented in Stage 5 via `transactions.category_manual`).

## Documentation

- Keep the `doc/` folder up to date. At the end of every task/approach, add or
  update the relevant doc so that another AI (or developer) with **no prior
  context** can understand the project quickly. The index is `doc/README.md`.
- Write code comments for a human developer: explain **why** a non-obvious
  decision was made (and what would break otherwise), not what the line does.
  Prioritise `parser/`, `service/` and any geometric/threshold logic.

## Backend state

- Follow a **stateless** approach on the backend: no per-request state kept in
  process memory (in-memory drafts, caches, counters, session-like stores).
  State belongs in the database or in the browser; a request must be answerable
  by any instance and must survive a restart.
- If a requirement genuinely cannot be implemented statelessly, **stop and ask
  me for explicit permission** before adding in-process state, and document the
  trade-off where the state lives.

## Other

- Do not use the term MVP when working on new parts and features of the project.
  We are not here to validate hypotheses or build minimal viable versions — we
  work in a "build it right, for the long term, and architecturally stable"
  paradigm. Components should be ready to scale, account for security, and be
  reusable in other parts of the system. During design, do not cut functionality —
  think several steps ahead. I will mark the boundaries of the nearest releasable
  version myself.
- Use the DRY approach. Before developing a new function or inventing a new
  style, make sure nothing similar already exists in the project.
- Keep it simple (KISS): do not over-complicate code without necessity.
- Before changing code to handle "legacy data" (rows missing a value only because
  they predate a later feature — i.e. something forgotten in test data during
  development), ask the user first. Resetting the test data is usually the
  simpler fix; do not add backfill/special-case code for data that exists only
  due to development history.
