## What and why
<!-- One or two lines. -->

Closes #

## Checklist
- [ ] Branch is `<type>/<issue#>-<slug>` and rebased on latest `develop`
- [ ] PR title is a conventional commit (`type(scope): subject`)
- [ ] Issue's "Done when" items are complete
- [ ] Tests added/updated and passing
- [ ] Tenant scope checked (if it touches data)
- [ ] No unrelated files changed, no migrations run
- [ ] Design pattern named in this description (or "none")
- [ ] Every new endpoint has a role rule plus an allowed-role and a denied-role test
- [ ] Errors go through the global handler with correct status codes
- [ ] No hardcoded credentials; `.env.example` updated
- [ ] New tables come from a new timestamped Flyway migration, no old one edited
- [ ] README updated if an endpoint, table or pipeline changed
- [ ] `docker compose up --build` still works (if running the app is affected)
