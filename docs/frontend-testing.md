# Frontend testing

Three layers, all in `frontend/`. Run them with pnpm from that folder.

| Layer | Command | Runs in CI | Covers |
|---|---|---|---|
| Unit and component | `pnpm test` | yes | `src/lib` helpers and server actions (backend mocked), components in jsdom |
| Coverage | `pnpm test:coverage` | no | the same tests with v8 coverage; HTML report in `frontend/coverage/` |
| End to end | `pnpm test:e2e` | no | real browser against a started stack |

## Unit and component tests

Vitest, `*.test.ts(x)` next to the file under test. Component tests start with
`// @vitest-environment jsdom`. Pure `lib` tests stay in the default node environment.

- Server actions are tested with their API module, `./session`, `next/cache` and `next/navigation`
  mocked (see `category-actions.test.ts`). Each covers validation errors (no backend call), the
  happy path, and the failure paths (409 on a field, 422, 4xx message, 5xx generic, unreachable).
- Form components mount with `src/components/form-test-harness.tsx` (`mountForm`, `typeInto`,
  `submit`, `unmountAll`). They assert accessibility as well as behaviour: label wiring,
  `aria-invalid` and `aria-describedby` on a bad field, focus moved to the field to fix,
  `role="alert"` for a failure and `role="status"` for a notice, and that typed values survive a
  failed submit.
- No snapshots.

## Coverage

`pnpm test:coverage` uses `@vitest/coverage-v8` (devDependency) over `src/**`. Route files under
`src/app/**` are server components and pages, which vitest does not render, so they show 0%; the
end-to-end layer covers them.

Run on 2026-11-27:

| | Tests | Statements | Branches | Functions | Lines |
|---|---|---|---|---|---|
| Before (#78) | 836 | 68.76% | 64.97% | 62.30% | 69.18% |
| After | 869 | 72.35% | 69.22% | 65.28% | 72.62% |

Weakest areas before this change: server actions for products, stock and sessions (5-15%), the
auth and organization forms (login, signup, invitation accept, invite, category: 0%), and every
`app/**` page. Still weak: `org-actions` (invite, role change paths), `meta-*`, `product-share-api`,
`app-rail`, `org-switcher`, `org-invitations`, `invitation-join-form`.

## End to end

Playwright, specs in `frontend/e2e/`. `playwright.config.ts` starts `pnpm dev` on port 3100.

- `smoke.spec.ts`, `organizations.spec.ts`: signed-out behaviour, need only the Next.js server.
- `workflows.spec.ts`: sign in (and a refused password), create a product (and its field error),
  open a conversation and assign it, share a product. These need the backend and real data, and are
  skipped with a message unless these variables are set (never commit values):

```bash
E2E_EMAIL=...        # an ADMIN of the organization
E2E_PASSWORD=...
E2E_ORG_ID=...       # organization id from /organizations/<id>
```

The organization needs one conversation and one product. The backend URL is whatever the frontend
is already configured with. List the specs without running them: `pnpm exec playwright test --list`.
CI (`.github/workflows/frontend.yml`) runs lint, `pnpm test` and build only, not e2e.
