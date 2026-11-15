# Workflow rules (humans and coding agents)

Stack: Next.js + TypeScript + Tailwind + pnpm (`frontend/`), Spring Boot + Maven (`backend/`), PostgreSQL + Flyway.

## Picking work
- Take only issues labelled `agent-ready` (agents) that are open, unassigned and not blocked. `human` issues are never started by an agent: comment and stop.
- Every issue listed under "Blocked by" must be closed first.
- Assign yourself before writing code. Assigned means taken. Never work on another person's issue.
- User stories (`US-*`) are trackers. Work the child tasks, never the story.

## Accounts (who commits and pushes)
Three collaborators, each logged in to `gh`. Work is done as the account the issue is assigned to, so history shows who built what.

| Account | Commit name | Noreply email |
|---|---|---|
| `Mutiur03` (admin) | `Mutiur Rahman` | `173971250+Mutiur03@users.noreply.github.com` |
| `bipro96` | `bipro96` | `243103502+bipro96@users.noreply.github.com` |
| `Aavas-99` | `Aavas Roy` | `159434864+Aavas-99@users.noreply.github.com` |

- Read the issue's assignee: `gh issue view <n> --json assignees --jq '.assignees[].login'`. Act as that account. No assignee: stop and ask.
- Never run `gh auth switch`. Select the account per command with a token, so accounts never mix:
  ```bash
  U=bipro96; ID=243103502   # login and id from the table; <Commit name> is the table's name column
  export GH_TOKEN=$(gh auth token -u $U)
  git -c user.name="<Commit name>" -c user.email=$ID+$U@users.noreply.github.com commit -m "feat(backend): ..."
  git push -u origin <branch>      # pushes as $U via GH_TOKEN
  gh pr create ...                 # PR author is $U
  ```
  Commit author and push account must both be the assignee. Use the same `GH_TOKEN` shell for every step of that issue, and do not leave it exported for the next issue.
- The commit name must be the account's exact GitHub profile name (table above), not the login. Otherwise GitHub treats author and merger as different people and adds a `Co-authored-by` trailer when squash-merging.
- Do not commit with the global git identity. Check with `git log -1 --format='%an <%ae>'` before pushing.
- Review: a PR is approved by a different collaborator than its author (`GH_TOKEN=$(gh auth token -u <reviewer>) gh pr review <n> --approve`). Merge is done by the author, or by `Mutiur03`. Admin bypass is for emergencies only.
- Only act as an account for issues assigned to it. Never comment, review or approve in another account's name on its own work.

**This rule is strict.** A commit by the wrong person is never acceptable, and it is enforced before the commit and before the push, not after:

- `.husky/pre-commit` and `.husky/pre-push` run `scripts/check-author.mjs`. It blocks when:
  - the author email is not one of the three noreply emails above (for example the global git identity);
  - the acting `gh` account (`GH_TOKEN`, else the active account) is not the commit author;
  - the branch is `<type>/<issue#>-<slug>` and that issue is unassigned or assigned to someone other than the author.
- Fix the cause (identity, `GH_TOKEN`, or assignee) and retry. Never use `--no-verify`, never rewrite the check, never commit "just this once" as someone else.
- A commit already made with the wrong author is rewritten before any push (`git rebase -r --exec` with the correct `--author`), never pushed.
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
- Clean messages only (strict): no `Co-authored-by` trailers, no "Generated with" or tool/AI attribution lines, no pasted bot summaries. Each commit has exactly one author. The commit-msg hook rejects them. Squash-merges use the PR title only (repo setting), so keep PR titles clean too.

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
