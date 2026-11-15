# Workflow rules (humans and coding agents)

Stack: Next.js + TypeScript + Tailwind + pnpm (`frontend/`), Spring Boot + Maven (`backend/`), PostgreSQL + Flyway.

## Picking work
- Take only issues labelled `agent-ready` (agents) that are open, unassigned and not blocked. `human` issues are never started by an agent: comment and stop.
- Every issue listed under "Blocked by" must be closed first.
- Assign yourself before writing code. Assigned means taken. Never work on another person's issue.
- User stories (`US-*`) are trackers. Work the child tasks, never the story.

## Branches
- One issue = one branch = one PR.
- Always branch from latest `main`: `git switch main && git pull && git switch -c <type>/<issue#>-<slug>`.
- Name: `feat/17-jwt-tokens`, `fix/54-meta-send-retry`, `chore/10-readme`. Types match Conventional Commits.
- Never push to `main`. Never force-push a branch someone else uses.

## Commits
- Conventional Commits, enforced by Husky + commitlint: `type(scope): subject`.
- Scopes: `frontend`, `backend`, `db`, `ci`, `docs`.
- Never use `--no-verify`.

## Pull requests
- Open a draft PR early, mark ready only when the issue's "Done when" list is complete and CI is green.
- Title is a conventional commit message (PRs are squash-merged, so the title becomes the commit). Body contains `Closes #<issue>`.
- Keep PRs small: one concern, ideally under ~400 changed lines. Split larger issues.
- Rebase on `main` before marking ready (`git fetch && git rebase origin/main`). No merge commits.
- 1 approval required. Author never merges their own unreviewed PR. Delete the branch after merge.

## Avoiding conflicts
- Stay inside your area: backend work in `backend/`, frontend work in `frontend/`. Do not reformat or touch unrelated files.
- Shared files (root `package.json`, `docker-compose.yml`, `.env.example`, `.github/`, `AGENTS.md`) change in their own small PR, merged fast, then everyone rebases.
- Flyway migrations: name by timestamp, `V<yyyyMMddHHmm>__<desc>.sql`, so parallel branches never collide on a version number. Never edit a migration already on `main`.
- Never run DB migrations (`flyway migrate`, `prisma migrate`, `db push` or equivalents) unless the user explicitly asks. Writing migration files is fine.
- Lockfiles (`pnpm-lock.yaml`, etc.): never hand-merge. On conflict, take `main`'s version and re-run the install.
- Respect dependencies: if your issue needs an unmerged one, wait or stack explicitly on that branch and say so in the PR.

## Definition of done
- Code, tests, and docs for the issue are in the PR.
- Anything touching tenant data is scoped to the organization (see ORG-05).
- CI is green, review approved, issue closed by the merge.
