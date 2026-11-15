# Workflow rules (humans and coding agents)

Stack: Next.js + TypeScript + Tailwind + pnpm (`frontend/`), Spring Boot + Maven (`backend/`), PostgreSQL + Flyway.

## Project structure (strict)
Put every file in its proper place from the first commit. A misplaced file is a review blocker.

**Backend** (`backend/`, standard Maven layout, same as IntelliJ generates)
- `src/main/java/com/connecta/backend/<feature>/`: package by feature (`config`, `auth`, `org`, `product`, `inbox`, `meta`, ...). Inside a feature use sub-packages by layer: `web` (controllers), `service`, `repository`, `domain` (entities), `dto`. Only `BackendApplication` lives in the root package.
- `src/test/java/...`: **mirrors the production package exactly.** The test for `a.b.Foo` is `a.b.FooTests`, in package `a.b`. Context or database tests are `FooIntegrationTests`. No catch-all test classes in the root package; a cross-cutting behavior is tested in the package of the class that implements it. Group with `@Nested`, not with extra classes.
- `src/main/resources/`: properties and `db/migration/`. Test-only resources go in `src/test/resources/`.

**Frontend** (`frontend/src/`)
- `app/`: routes only (`page`, `layout`, route handlers, route-private pieces).
- `components/`: shared UI components. `lib/`: non-UI code (API client, helpers).
- Tests sit next to the file they test: `name.test.ts(x)`. End-to-end tests go in `frontend/e2e/`.

**Repo root**: `backend/`, `frontend/`, `scripts/` (tooling), `.github/` (templates, workflows). No application code at the root.

## Picking work
- Take only issues labelled `agent-ready` (agents) that are open, unassigned and not blocked. `human` issues are never started by an agent: comment and stop.
- Every issue listed under "Blocked by" must be closed first.
- Assign yourself before writing code. Assigned means taken. Never work on another person's issue.
- User stories (`US-*`) are trackers. Work the child tasks, never the story.

## Accounts (who commits and pushes)
Three collaborators, each logged in to `gh`. Work is done as the account the issue is assigned to, so history shows who built what.

| Account | Commit email (the one GitHub itself uses for the account) |
|---|---|
| `Mutiur03` (admin) | `mutiur5bb@gmail.com` |
| `bipro96` | `bb123biprobiswas@gmail.com` |
| `Aavas-99` | `roy.aavas@gmail.com` |

Local commits use the same email GitHub uses when it makes a commit for that account (merge buttons, web editor), so author and committer are the same identity everywhere and GitHub never adds a `Co-authored-by` line. This only holds while each account has "Keep my email addresses private" **off** in GitHub Settings -> Emails. If someone turns it on, GitHub switches to a noreply address and this table must change. These emails are public in the repository history.

Commits made before this change use `<id>+<login>@users.noreply.github.com` (Mutiur03 173971250, bipro96 243103502, Aavas-99 159434864). They stay valid and are never rewritten on `main`.

- Read the issue's assignee: `gh issue view <n> --json assignees --jq '.assignees[].login'`. Act as that account. No assignee: stop and ask.
- Never run `gh auth switch`. Act as an account with the helper, once per shell, so accounts never mix:
  ```bash
  source scripts/use-account.sh bipro96   # sets git author AND committer plus GH_TOKEN
  git switch main && git pull --ff-only
  git switch -c feat/58-availability
  git commit -m "feat(backend): ..."      # author and committer are bipro96
  git push -u origin feat/58-availability # pushes as bipro96
  gh pr create ...                        # PR author is bipro96
  ```
  Use one shell per account. Every git command in it (commit, amend, rebase, cherry-pick) and every `gh` call uses that identity, so a rebase can never fall back to your global git config. Start a new shell, or source the helper again, before acting as someone else.
- Do not rely on the global git identity. Check with `git log -1 --format='%an <%ae> | %cn <%ce>'` before pushing: both halves must be the same person.
- Keep local and remote in sync: after a PR merges, run `git fetch --prune && git switch main && git pull --ff-only && git branch -d <branch>`. Do not stack PRs; start a dependent branch only after the one it needs has merged, so nothing has to be rebased or force-pushed.
- Review: a PR is approved by a different collaborator than its author (`GH_TOKEN=$(gh auth token -u <reviewer>) gh pr review <n> --approve`). Merge is done by the author, or by `Mutiur03`. Admin bypass is for emergencies only.
- Only act as an account for issues assigned to it. Never comment, review or approve in another account's name on its own work.

**This rule is strict.** A commit by the wrong person is never acceptable, and it is enforced before the commit and before the push, not after:

- `.husky/pre-commit` and `.husky/pre-push` run `scripts/check-author.mjs`. It blocks when:
  - the author or the committer email is not one of the three collaborators' emails;
  - the committer is a different person than the author (this is what a rebase run with the wrong identity produces);
  - the acting `gh` account (`GH_TOKEN`, else the active account) is not the commit author;
  - the branch is `<type>/<issue#>-<slug>` and that issue is unassigned or assigned to someone other than the author.
- Fix the cause (identity, `GH_TOKEN`, or assignee) and retry. Never use `--no-verify`, never rewrite the check, never commit "just this once" as someone else.
- A commit already made with the wrong author or committer is rewritten before any push (`source scripts/use-account.sh <login>` then `git commit --amend --no-edit --reset-author`), never pushed.
- The check is local-only by design; there is no CI substitute, so skipping hooks means the rule is broken.

## Branches
- One issue = one branch = one PR.
- Always branch from latest `main`: `git switch main && git pull && git switch -c <type>/<issue#>-<slug>`.
- Name: `feat/17-jwt-tokens`, `fix/54-meta-send-retry`, `chore/10-readme`. Types match Conventional Commits.
- Never push to `main`. Never force-push a branch someone else uses.

## Commits
- Conventional Commits, enforced by Husky + commitlint: `type(scope): subject`.
- Scopes: `frontend`, `backend`, `db`, `ci`, `docs`.
- Never use `--no-verify`.
- Clean messages only (strict): no `Co-authored-by` trailers, no "Generated with" or tool/AI attribution lines, no pasted bot summaries. Each commit has exactly one author. The commit-msg hook rejects them. PRs are rebase-merged: every commit lands on `main` exactly as authored (squash-merge is disabled because GitHub rewrites the author and adds a `Co-authored-by` trailer).

## Pull requests
- Open a draft PR early, mark ready only when the issue's "Done when" list is complete and CI is green.
- Title is a conventional commit message. Body contains `Closes #<issue>`. Each commit in the PR must itself be clean and conventional, because rebase-merge puts every commit on `main`: squash fixups and tidy history (`git rebase -i`) before marking ready.
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
