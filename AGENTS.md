# Workflow rules (humans and coding agents)

Stack: Next.js + TypeScript + Tailwind + pnpm (`frontend/`), Spring Boot + Maven (`backend/`), PostgreSQL + Flyway.

## Project structure (strict)
Put every file in its proper place from the first commit. A misplaced file is a review blocker.

**Backend** (`backend/`, standard Maven layout, same as IntelliJ generates)
- `src/main/java/com/connecta/backend/<feature>/`: package by feature (`config`, `auth`, `org`, `product`, `inbox`, `meta`, ...). Inside a feature use sub-packages by layer: `controller` (REST controllers + exception handlers), `service`, `repository`, `domain` (entities), `dto`. Only `BackendApplication` lives in the root package.
- `src/test/java/...`: **mirrors the production package exactly.** The test for `a.b.Foo` is `a.b.FooTests`, in package `a.b`. Context or database tests are `FooIntegrationTests`. No catch-all test classes in the root package; a cross-cutting behavior is tested in the package of the class that implements it. Group with `@Nested`, not with extra classes.
- `src/main/resources/`: properties and `db/migration/`. Test-only resources go in `src/test/resources/`.

**Frontend** (`frontend/src/`)
- `app/`: routes only (`page`, `layout`, route handlers, route-private pieces). Two route groups: `(app)` is the signed-in shell with the sidebar, `(auth)` is login, signup, setup and invitation acceptance with no sidebar. A new signed-in page goes in `(app)`, a new public page in `(auth)`.
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
  git switch develop && git pull --ff-only
  git switch -c feat/58-availability
  git commit -m "feat(backend): ..."      # author and committer are bipro96
  git push -u origin feat/58-availability # pushes as bipro96
  gh pr create ...                        # PR author is bipro96
  ```
  Use one shell per account. Every git command in it (commit, amend, rebase, cherry-pick) and every `gh` call uses that identity, so a rebase can never fall back to your global git config. Start a new shell, or source the helper again, before acting as someone else.
- Do not rely on the global git identity. Check with `git log -1 --format='%an <%ae> | %cn <%ce>'` before pushing: both halves must be the same person.
- Keep local and remote in sync: after a PR merges, run `git fetch --prune && git switch develop && git pull --ff-only && git branch -d <branch>`. Do not stack PRs; start a dependent branch only after the one it needs has merged, so nothing has to be rebased or force-pushed.
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

## Branch model
- `main`: protected, always deployable, receives releases only. The VPS deploy runs from it.
- `develop`: protected staging branch. All feature PRs target it. `main` stays the default branch, so `Closes #<n>` does not fire on a merge into `develop`: close the issue by hand once the PR is merged (comment the PR link), or let the release PR close it.
- Feature branches: `<type>/<issue#>-<slug>` from `develop`, one PR each.
- Release: when `develop` is green and demo-ready, open a PR `develop` -> `main` titled `chore: release <date or version>`, 1 approval, merged with a **merge commit** (not squash), so `main` keeps each feature commit from `develop` and shows every release as one merge point. Nobody pushes to either branch directly.
- Both branches require a PR, 1 approval, no force push, no deletion. Merge method differs per branch (enforced by the rulesets `protect-develop` and `protect-main`): `develop` accepts squash only, `main` accepts merge commit only.

## Roles and onboarding (how people get an account)
- **OWNER** is the system owner of the SaaS: global, tied to no organization, sees every organization (`/admin/**`, `/admin/organizations` in the UI). There is exactly one owner path: `/setup` on a system that has none. It is guarded by the single-row `system_setup` table, so two simultaneous first visits cannot both win. After an owner exists `/setup` redirects to `/signup`, and `/login` and `/signup` redirect to `/setup` until then.
- **ADMIN** is made by public `/signup`. An admin creates organizations and becomes their admin.
- **AGENT** is never made by signup. Agents join only through an emailed invitation (`/invitations/accept?token=`). A new person creates their account on that page, an existing one signs in first.
- **Organization roles** (`memberships`, `invitations`) are only `ADMIN` and `AGENT`. There is no org-level owner. An admin may invite another admin or an agent. The last admin of an organization cannot leave or be demoted (`LastAdminException`, 422).
- Invitation email goes through the `InvitationMailer` interface: SMTP when `SMTP_HOST` is set, otherwise a no-op that logs "not sent" (the admin can still copy the link). Dev uses Mailpit (`docker compose --profile dev up`, inbox at `localhost:8025`). Settings: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_STARTTLS`, `MAIL_FROM`, `FRONTEND_URL` (all in `.env.example`; `prod` has no defaults for `MAIL_FROM` and `FRONTEND_URL`).
- Dev seed data does not exist. The database starts empty; roles are the only seeded rows.

## Course requirements (graded, read before coding)
Source: CSE 3220 lab guidelines. These are marks, and the starred items are automatic failures if missing.
- Design patterns*: every feature uses a named pattern (Strategy, Factory, Adapter, Observer, Builder, Repository, Template Method, ...). The PR description names it. Keep `docs/design-patterns.md` listing pattern -> class -> why.
- Role-based access*: every endpoint states its role rule (`SecurityConfig` or `@PreAuthorize`), with one allowed-role and one denied-role test. Roles are `OWNER > ADMIN > AGENT` (see "Roles and onboarding").
- Tests*: at least 20 service-layer unit tests (JUnit, Mockito) and 10 controller-layer integration tests (`@SpringBootTest`, MockMvc). Every PR with logic ships tests. They must pass in CI.
- Docker*: `docker compose up --build` must keep working (app + PostgreSQL, configuration from environment variables only, no hardcoded credentials, `.env.example` current).
- Deployment*: the app stays deployable from `main` to the team's VPS (Docker Compose behind Caddy, see `docs/deployment.md`), with CI/CD deploying automatically. The course text says Render; the team deploys to a VPS instead, by decision.
- No direct push to `main`* (enforced by the ruleset).
- REST: correct HTTP methods and status codes, one global exception handler (problem-detail JSON, no stack traces), at least 5 controllers and CRUD on at least 3 entities.
- Database: at least 6 tables with 1:M, M:1 and M:M relationships through JPA, all created by Flyway migrations.
- Code quality: clear names, small methods, no swallowed exceptions.
- README: description, architecture diagram, ER diagram, API endpoints, run steps, CI/CD explanation. A PR that changes an endpoint, a table or the pipeline updates the matching README section.
- Bonus: Actuator (done), Prometheus metrics, Grafana dashboard, a second deployment platform.

### Objective and team
- Goal: a small but complete web app showing a professional workflow (Spring Boot, PostgreSQL, Spring Security, Docker, GitHub CI/CD, a VPS). Feature richness is not the goal.
- Team: exactly three members, the three collaborators in Accounts. Every member must have visible commits and merged PRs.

### Detail the guidelines require
- Auth: registration, login/logout, BCrypt, role-based authorization by method-level or URL rules. The guidelines name ADMIN, SELLER, BUYER; we use `OWNER`, `ADMIN`, `AGENT`. The README states this mapping so graders see three-tier role-based access.
- Docker: a `Dockerfile` and a `docker-compose.yml` with the application and PostgreSQL containers. Credentials only through environment variables. The app must start with `docker compose up --build`.
- Git: `main` (protected), `develop`, feature branches, no direct push to `main`, PR with at least one review approval. Branch protection must be configured (5 marks).
- CI/CD: a GitHub Actions workflow builds the project, runs the tests, and deploys to the VPS automatically from `main` (images to GHCR, SSH, health check, rollback) (5 marks for the workflow, 5 for working auto-deploy).
- Deployment: public URL of the VPS deployment (the guidelines say Render; we use a VPS), app runs without errors. Submit the live URL and the repository link.

### Deliverables
- GitHub repository.
- README with project description, architecture diagram, ER diagram, API endpoints, run instructions, CI/CD explanation.
- Deployed URL.
- 15-minute demo presentation.

### Marking (100) and where agents lose marks
| Area | Marks | Breakdown |
|---|---|---|
| Design patterns and code quality | 20 | patterns 10, clean code and naming 5, exception handling 5 |
| Security and role management | 15 | Spring Security 5, password encryption 3, role-based access enforced 7 |
| Testing | 15 | unit test quality 7, integration tests 5, tests run in CI 3 |
| Dockerization | 10 | Dockerfile 4, compose works 4, environment config 2 |
| CI/CD and Git workflow | 15 | branch protection 5, Actions workflow 5, automatic deployment 5 |
| Database design | 10 | proper entity relationships |
| Deployment and demo | 10 | app runs without error 5, demonstration 5 |
| Documentation | 5 | clear README on GitHub |
- Bonus (up to 20): Monitoring (10): Actuator 2, Prometheus application metrics 4, working Grafana dashboard 4. Additional deployment (10): a platform other than Render 5 (the VPS satisfies this line, since the team does not use Render), deployment documentation in the README 5.

### Automatic failure (any one fails the project)
No design patterns, no role-based access control, direct push to `main`, no Dockerization, tests not implemented, app not deployed.

### What is evaluated
Small system design, clean architecture, security, tests, professional Git use, CI/CD, Docker, and production-ready deployment. A task is not done if it breaks any item above.

## Engineering standards (graded: follow exactly)

### Layering
- `controller` -> `service` -> `repository` -> `domain`. A controller never touches a repository or an entity directly; it takes a request DTO and returns a response DTO. A repository is only called from a service. Entities never leave the service layer and never appear in JSON.
- Business rules live in services (or in the entity when it is a rule about that entity alone). Controllers only validate input, call one service method, and map the result to a status code.
- Constructor injection only (no field `@Autowired`); Lombok `@RequiredArgsConstructor` generates it. Services are `@Service`; transactions are declared on service methods (`@Transactional`, `readOnly = true` for reads).
- Cross-feature calls go through the other feature's service, never its repository.

### SOLID (applies to all new and changed code; reviewers check it)
- **S, single responsibility:** a class has one reason to change. A service that does two jobs (issuing tokens and storing them, parsing and persisting) is split. A method does one thing.
- **O, open/closed:** a new variant (assignment rule, channel, parser) is a new class behind an existing interface, never a new `if`/`switch` branch in old code. Chosen by a factory or Spring injection (see Design patterns).
- **L, Liskov:** every implementation honors its interface contract: same inputs accepted, no new checked or surprise exceptions, no `UnsupportedOperationException` stubs. A shared contract test or the same test per variant proves it.
- **I, interface segregation:** keep interfaces small and role-specific (`ChannelClient` sends, it does not also parse webhooks). Do not force an implementation to carry methods it does not use.
- **D, dependency inversion:** depend on abstractions (`PasswordEncoder`, `Clock`, `JwtEncoder`, our own strategy/adapter interfaces), injected through the constructor. Never `new` a collaborator inside a service, and never read a static singleton, when an injected one exists: two copies drift apart.
- **Interfaces only where they pay:** introduce one when there are two or more implementations, or a boundary to an outside system (Meta API, clock). One implementation and no boundary means a plain class. Do not create an interface to tick SOLID.
- **Constructors:** `@RequiredArgsConstructor` by default. Write the constructor by hand only when it must run code (for example derive a value from an injected dependency, as `AuthService` does). Keep the fields `final`.
- The PR description names the SOLID principle a refactor or new abstraction serves, next to the design pattern.

### Design patterns (at least one per feature, named in the PR and listed in `docs/design-patterns.md`)
Use a pattern only where it removes a real problem; do not wrap code in a pattern to tick a box. Planned uses:

| Pattern | Where in Connecta |
|---|---|
| Repository | every persistence access goes through a Spring Data repository |
| DTO + mapper | request/response `record`s with a static `from(entity)` factory (see `auth/dto`) |
| Strategy | invitation delivery: `InvitationMailer` with `SmtpInvitationMailer` and `NoopInvitationMailer` (built); conversation assignment: an `AssignmentStrategy` with round-robin, least-loaded and manual variants |
| Adapter | channel providers: one `ChannelClient` interface, one adapter each for Instagram and Messenger, so the inbox never knows Meta's API |
| Factory | picks the `ChannelClient` / `AssignmentStrategy` for an organization's settings |
| Observer | Spring `ApplicationEventPublisher`: `InvitationCreatedEvent` is sent after commit (`@TransactionalEventListener`) and the mail listener reacts (built); a new message publishes an event; assignment, notifications and live updates listen |
| Template Method | webhook intake: shared verify -> parse -> dedupe -> persist skeleton, per-channel parsing step |
| Chain of Responsibility | the Spring Security filter chain (already in place) |
| Builder | test data and complex immutable objects |

When you add a pattern: put it in clearly named classes (`...Strategy`, `...Adapter`, `...Factory`), add a unit test per variant, and add a row (pattern, classes, reason) to `docs/design-patterns.md`.

### REST conventions
- Base path `/api`. Resources are plural lowercase nouns in kebab-case: `/api/organizations`, `/api/organizations/{orgId}/members`, `/api/conversations/{id}/messages`. No verbs in paths; a non-CRUD action is a sub-resource (`POST /api/conversations/{id}/assignment`).
- Methods: `GET` read (safe, no side effects), `POST` create, `PUT` full replace, `PATCH` partial update, `DELETE` remove. Idempotent methods stay idempotent.
- Status codes: `200` read or update with a body, `201` create (with a `Location` header), `204` delete or no body, `400` malformed or invalid input, `401` not authenticated, `403` authenticated but not allowed, `404` not found (also for another tenant's data, so ids are not leaked), `409` conflict (duplicate, state clash), `422` a well-formed request that breaks a business rule, `500` never on purpose.
- JSON: camelCase fields, ISO-8601 UTC timestamps, UUID ids, enums as strings. Never expose password hashes, token hashes, or another tenant's data.
- Lists are paginated with `page` and `size` (default 20, max 100) and a stable `sort`, and return `{content, page, size, totalElements}`. Never return an unbounded list.
- Validation: Jakarta Bean Validation annotations on request records (`@NotBlank`, `@Size`, `@Email`) and `@Valid` on the controller parameter. Mirror the DB limits (see `RegisterRequest`).
- Every endpoint is documented for OpenAPI (summary, response codes) and listed in the README's endpoint table.

### Exception handling
- Services throw small domain exceptions named for the problem (`EmailAlreadyUsedException`), never bare `RuntimeException` or `Exception`, and never return `null` to signal failure.
- Exceptions are mapped to `ProblemDetail` (RFC 9457) in one place: feature handlers in `<feature>/controller/<Feature>ExceptionHandler`, the shared fallback in `common/controller/GlobalExceptionHandler`. Controllers have no try/catch.
- Error bodies have `status`, `title`, `detail` (and `errors` per field for validation). No stack traces, SQL, class names or secrets in a response. Unknown errors are logged and answered with a generic `500`.
- Authentication failures give the same message for an unknown user and a wrong password.
- Each new exception gets a MockMvc test that checks the status and body.

### Security
- Default is deny: a new path is authenticated unless it is added to the public list on purpose. Admin-only paths sit under `/api/admin/**`.
- Authorization is stated twice: a URL rule in `SecurityConfig` for coarse access, and `@PreAuthorize` on service methods for fine rules (for example "only an admin of this organization").
- Passwords only as BCrypt. Secrets (`JWT_SECRET`, DB password, Meta tokens) only from the environment, never in code, tests, logs or the README.
- Tenant isolation: every query on tenant data is filtered by organization, and a cross-tenant id returns `404`. Each such feature has a test for the cross-tenant case.

### Database and JPA
- Schema changes only through new Flyway migrations (`V<yyyyMMddHHmm>__desc.sql`); `ddl-auto=validate`.
- Foreign keys, `NOT NULL`, unique and check constraints belong in the migration, not only in Java. Index every foreign key and every column used in a `WHERE` or join.
- Relationships: `@ManyToOne(fetch = LAZY)` by default; map the owning side explicitly; no `CascadeType.ALL` or `EAGER` without a written reason. Use `@EntityGraph` or a fetch join to avoid N+1 queries on lists.
- Entities: Lombok `@Getter` and `@NoArgsConstructor(access = AccessLevel.PROTECTED)` are allowed; never `@Data`, `@Setter`, `@EqualsAndHashCode` or `@ToString` (lazy relations, id-based equality). Keep a meaningful public constructor; `equals`/`hashCode` on the id. No logic inside an entity that needs a repository.
- The six-table minimum with 1:M, M:1 and M:M relationships must stay visible in the README's ER diagram.

### Code quality
- Package by feature. Names say what the thing does (`RefreshTokenService.rotate`, not `process`). Methods are short and single-purpose. No commented-out code, no dead code, no `TODO` without an issue number.
- No magic numbers or strings: use constants, enums or `@ConfigurationProperties` (see `JwtProperties`).
- Logging through SLF4J (never `System.out`), at the right level, without personal data or secrets.
- Lombok is allowed (`@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor`, `@Slf4j`, `@Builder`); keep it `optional` in the pom. Not on DTOs: use `record`.
- Prefer `record` for DTOs and immutability. `Optional` only as a return type.
- Frontend: TypeScript strict, no `any`, API calls only through `src/lib`, server actions for mutations, no secrets in client code.
- Frontend UI building blocks (use them, do not re-style per page):
  - Buttons: `Button` in `components/ui/button.tsx`. Default height 40px to match fields, `size="sm"` (32px) only inside table rows, `block` only for sign-in style forms, never a full-width bar elsewhere. Variants: `primary`, `secondary`, `danger`, `destructive` (solid red, confirm dialogs only).
  - A label that swaps while pending uses `PendingLabel`, so the button never changes width.
  - Never `window.confirm`, `alert` or `prompt`. A confirmation is the native `<dialog>` in `OrgActionButton` (`confirm` prop).
  - Tables sit in `TableScroll`, which is the rounded, bordered panel. Cells have 16px side padding, rows are 56px, cells are vertically centred, row actions are right-aligned.
  - Field errors show on the field only. The form alert line (`alertText`) shows only a failure from the action itself, never a copy of a field error.
  - Do not ask for what can be derived: an organization slug is generated from its name on the server (`slugify`, retried with a suffix on a 409).
  - Theme: the pinned theme is a `theme` cookie that `app/layout.tsx` turns into `<html data-theme>`, so the first paint is right. Do not add a script to apply a theme.
  - The sidebar (`components/app-rail.tsx`) holds the organization switcher (`org-switcher.tsx`); the current organization is the one in the URL, else the `connecta_org` cookie, else the first. Actions that change the organization list call `refreshSidebar()` in `lib/org-actions.ts`.

### Testing
- Unit tests (service layer): JUnit 5 and Mockito, no Spring context, `@Nested` per behavior. One happy path and every failure path for each public service method. Each pattern variant (every strategy, every adapter) has its own test.
- Integration tests (controller layer): `@SpringBootTest` with MockMvc per controller: status, body, validation errors, `401`, `403` and the cross-tenant `404`. Database-backed tests run against real PostgreSQL once the harness exists.
- Tests are deterministic (inject `Clock`, no sleeps, no order dependence) and mirror the production package (see Project structure). `./mvnw test` passes locally and in CI before every PR.
- Keep the totals above 20 unit and 10 integration tests; the PR description lists the tests added.

### Docker, configuration and CI/CD
- `backend/Dockerfile` is multi-stage: build with Maven, run on a slim JRE image as a non-root user, with a `HEALTHCHECK` on `/actuator/health`. A frontend Dockerfile follows the same idea.
- `docker-compose.yml` runs the app, PostgreSQL and the frontend (plus Mailpit under the `dev` profile) with named volumes and a `depends_on` healthcheck, and takes every credential from `.env` (documented in `.env.example`). `docker compose up --build` from a clean clone must work.
- Configuration: one `backend/src/main/resources/application.yml`, no Spring profiles. Per-environment values come only from environment variables (`backend/.env` locally, see `.env.example`). Secrets have no default in the file; Swagger is off unless `SWAGGER_ENABLED=true`.
- Workflows in `.github/workflows`: build and test on every PR; on push to `main` build, test and deploy to the VPS automatically. Maven and pnpm caches are enabled. A red build blocks the merge.
- Observability (bonus): Actuator health, info and metrics, a Micrometer Prometheus endpoint, and a Grafana dashboard JSON committed under `ops/`.

### Frontend design and quality (every UI change)
**The design is fixed: read `docs/design-brief.md` (the source of truth) and `docs/frontend-design.md` (how it maps to code) before any UI work.** Calm, dense, ink-and-bone, one navy accent, brass highlight for unread/status dots only, rows and dividers instead of cards, Geist at 13px, 150ms ease-out, light and dark from one token scale, channel identity as small badges only. Use the tokens and base components, never raw colours, and run the brief's anti-slop checklist on every screen. A change that departs from the brief updates the brief in the same PR, with the human's agreement.

Load these skills with the Skill tool **before** designing or writing UI, not after. They are part of the work, not optional polish. The brief decides the direction; the skills help execute it.
1. **`frontend-design:frontend-design`**: work within the direction in `docs/design-brief.md`: do not pick a new aesthetic. Use it for layout, hierarchy and detail. No templated defaults.
2. **`modern-web-guidance:modern-web-guidance`**: run it first for HTML, CSS and client-side JS (forms, dialogs, layout, motion, Core Web Vitals). Prefer native platform features over libraries.
3. **Web design guidelines** (accessibility, forms, focus, contrast, touch targets, reduced motion, responsive layout): run `design:accessibility-review` (WCAG 2.1 AA) on every new or changed screen before the PR. If a dedicated web-design-guidelines skill is installed, load it too.
4. **React and Next.js best practices:** first read the guide for the installed version in `frontend/node_modules/next/dist/docs/` (see `frontend/AGENTS.md`; this Next.js differs from older versions). Server Components by default, `"use client"` only where state or browser APIs need it, data fetched on the server in the page or in a Server Component, with no loading skeletons or `loading.tsx` (Cache Components are off on purpose, so pages render complete; do not turn `cacheComponents` back on without the human's agreement), no request waterfalls (use `Promise.all`), server actions for mutations, `next/image` and `next/font`, no `any`. If a React/Next best-practices skill is installed, load it too.
5. **`unslop`:** run it on each finished screen to remove AI-looking defaults (gradients, glows, emoji labels, uneven spacing).
6. **Prove it in the running app.** Unit tests are not enough for UI: start the stack (`docker compose up --build`, only when the human has asked for migrations to run), drive the changed flow in a browser, and check the console for errors. Say in the PR what you clicked through.
- The PR description lists which of these skills were used and what they changed. A subagent brief for frontend work must name them so the agent loads them too.

## Agent playbook (how to run one task end to end)
Follow in order. Stop and ask the human at any "stop" line.

1. **Pick.** Open, unassigned, `agent-ready`, all "Blocked by" issues closed. Read the issue, its parent story and its milestone. Stop if it is `human` or blocked.
2. **Act as the assignee.** `gh issue view <n> --json assignees`, then `source scripts/use-account.sh <login>` in a fresh shell. Stop if there is no assignee.
3. **Branch.** `git fetch --prune && git switch develop && git pull --ff-only && git switch -c <type>/<n>-<slug>`. Never branch from `main` or from another feature branch.
4. **Plan against the course rules and the Engineering standards.** Before coding, name the design pattern the change uses, the roles allowed on each new endpoint, the tables it touches (migration needed?), and the tests it needs (unit for services, MockMvc for controllers). Put each file where "Project structure" says.
   For UI work, also load the skills in "Frontend design and quality" now.
5. **Build small.** One concern, about 400 changed lines at most. Commit as you go with conventional messages, no trailers, body lines under 100 characters. Never `--no-verify`.
6. **Prove it.** Run `./mvnw test` (on Windows `mvnw.cmd`) and the frontend `pnpm lint`, `pnpm test`, `pnpm build` for the areas touched. For anything that needs a database, run it against real PostgreSQL, since mocks hid two bugs before. Never run migrations unless the human asks in that message. Fix failures; never weaken or delete a test to pass.
7. **Self-check before the PR** (every box must be true):
   - [ ] Pattern named in the PR description
   - [ ] SOLID checked: no new `if`/`switch` for a variant, no `new` collaborator inside a service, no fat interface
   - [ ] Every new endpoint has a role rule plus allowed and denied tests
   - [ ] Errors go through the global handler, correct status codes
   - [ ] No hardcoded credentials, `.env.example` updated
   - [ ] New tables come from a new timestamped Flyway migration, never an edited old one
   - [ ] README updated if an endpoint, table or pipeline changed
   - [ ] Tenant data is scoped to the organization
   - [ ] `docker compose up --build` still works if the change affects running the app
   - [ ] Frontend change: follows `docs/design-brief.md` (tokens only, anti-slop checklist run); design, web-guidance, accessibility, React/Next and `unslop` skills used and listed in the PR, and the flow checked in a running app
8. **Push and open the PR** against `develop`: title is a conventional commit, body has `Closes #<n>` (it will not auto-close, see Branch model), the pattern, and what was run. Draft first, ready when the issue's "Done when" list is complete and CI is green.
9. **Review.** A different collaborator approves with their own account. Never approve your own work or act as another account on its work. Wait for every required check to be green; do not merge on pending.
10. **Merge.** Squash-merge into `develop` (merge commits are not allowed there), done by the author or `Mutiur03`. Then `git fetch --prune && git switch develop && git pull --ff-only && git branch -d <branch>`.
11. **Close the issue by hand** after the merge: tick its "Done when" boxes and comment the PR link. When all tasks of a story are done, close the story with a short summary comment.
12. **Release to `main`.** Only on a human's request. Open a PR `develop` -> `main` titled `chore: release <date>`, with the list of merged PRs and a pass of the Course requirements checklist. After it merges, the deploy workflow ships it to the VPS; check the live URL and `/actuator/health`.

**Stop and ask** when: the issue is blocked, unassigned, or `human`; a rule here conflicts with the issue; a step needs a secret, a paid service, a Meta account, a database migration run, or a repo-settings change; CI is red for a reason outside the change; or the change would break a graded requirement.

## Branches
- One issue = one branch = one PR.
- Always branch from latest `develop`: `git switch develop && git pull && git switch -c <type>/<issue#>-<slug>`.
- Name: `feat/17-jwt-tokens`, `fix/54-meta-send-retry`, `chore/10-readme`. Types match Conventional Commits.
- Never push to `main` or `develop`. Never force-push a branch someone else uses.

## Commits
- Conventional Commits, enforced by Husky + commitlint: `type(scope): subject`.
- Scopes: `frontend`, `backend`, `db`, `ci`, `docs`.
- Never use `--no-verify`.
- Clean messages only (strict): no `Co-authored-by` trailers, no "Generated with" or tool/AI attribution lines, no pasted bot summaries. Each commit has exactly one author. The commit-msg hook rejects them. PRs are squash-merged, so each PR becomes one commit on `develop`: the PR title is that commit's message and must be a clean conventional commit. Squash is the only allowed merge method on `develop`; a release PR into `main` is a merge commit. It stays clean because each account's local email equals the one GitHub uses (see Accounts).

## Pull requests
- Open a draft PR early, mark ready only when the issue's "Done when" list is complete and CI is green.
- Title is a conventional commit message. Body contains `Closes #<issue>`. Each commit in the PR must still be clean and conventional (the commit hooks check them), but the PR title is what lands on `develop`.
- Keep PRs small: one concern, ideally under ~400 changed lines. Split larger issues.
- Rebase on `develop` before marking ready (`git fetch && git rebase origin/develop`). No merge commits in feature branches.
- Feature PRs target `develop`, never `main`. 1 approval required. Author never merges their own unreviewed PR. Delete the branch after merge.

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
