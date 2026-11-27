# Testing

All backend tests run through Surefire with `./mvnw test` (Windows: `.\mvnw.cmd test`). There is no
separate unit/integration phase: both kinds live in `backend/src/test/java`, mirroring the production
packages, and CI runs the same command (`.github/workflows/backend.yml`). Database-backed tests use
Testcontainers PostgreSQL, so Docker must be running.

## Unit vs integration

| Kind | Naming | Tools | Run one |
|---|---|---|---|
| Service/unit | `FooTests` | JUnit 5, Mockito, no Spring context, injected `Clock` | `.\mvnw.cmd -Dtest=MemberServiceTests test` |
| Controller/integration | `FooIntegrationTests` | `@SpringBootTest`, MockMvc, real PostgreSQL | `.\mvnw.cmd -Dtest=*IntegrationTests test` |

Course minimums are 20 service unit tests and 10 controller integration tests. Current counts
(`@Test` and `@ParameterizedTest` methods): about 970 service-layer unit tests, about 640 integration
tests, 1706 test executions in total, all green.

## Coverage

Coverage is opt-in so default builds and CI are unchanged:

```
.\mvnw.cmd -Pcoverage test
```

The report is `backend/target/site/jacoco/index.html` (`jacoco.csv` and `jacoco.xml` beside it).

Per-package results, before and after the QA-01 gap tests (line and branch):

| Package | Line before | Line after | Branch before | Branch after |
|---|---|---|---|---|
| (root) | 20.0% | 20.0% | 0.0% | 0.0% |
| auth.controller | 98.0% | 98.0% | 83.3% | 83.3% |
| auth.domain | 88.9% | 88.9% | 42.9% | 42.9% |
| auth.dto | 100.0% | 100.0% | 100.0% | 100.0% |
| auth.service | 90.0% | 90.0% | 75.0% | 75.0% |
| common.controller | 100.0% | 100.0% | 83.3% | 83.3% |
| common.dto | 100.0% | 100.0% | 0.0% | 0.0% |
| config | 96.0% | 96.0% | 77.8% | 77.8% |
| file.controller | 100.0% | 100.0% | 100.0% | 100.0% |
| file.domain | 81.8% | 81.8% | 0.0% | 0.0% |
| file.service | 100.0% | 100.0% | 100.0% | 100.0% |
| inbox.controller | 90.9% | 90.9% | 0.0% | 0.0% |
| inbox.domain | 90.7% | 90.7% | 46.0% | 46.0% |
| inbox.dto | 100.0% | 100.0% | 92.9% | 92.9% |
| inbox.repository | 96.0% | 96.0% | 66.7% | 66.7% |
| inbox.service | 97.1% | 97.1% | 92.6% | 92.6% |
| mail.service | 100.0% | 100.0% | 100.0% | 100.0% |
| meta.controller | 86.4% | 93.2% | 0.0% | 0.0% |
| meta.domain | 81.1% | 81.1% | 26.0% | 26.0% |
| meta.dto | 78.6% | 78.6% | 0.0% | 0.0% |
| meta.service | 95.9% | 95.9% | 85.3% | 85.3% |
| org.controller | 100.0% | 100.0% | 0.0% | 0.0% |
| org.domain | 92.8% | 92.8% | 51.8% | 51.8% |
| org.dto | 100.0% | 100.0% | 87.5% | 87.5% |
| org.service | 98.7% | 99.3% | 95.7% | 98.6% |
| product.controller | 90.2% | 90.2% | 0.0% | 0.0% |
| product.domain | 94.1% | 94.1% | 52.7% | 52.7% |
| product.dto | 100.0% | 100.0% | 92.9% | 92.9% |
| product.repository | 100.0% | 100.0% | 100.0% | 100.0% |
| product.service | 98.9% | 99.6% | 98.5% | 98.5% |
| realtime.controller | 74.3% | 100.0% | 66.7% | 100.0% |
| realtime.dto | 100.0% | 100.0% | 0.0% | 0.0% |
| realtime.service | 98.8% | 100.0% | 88.6% | 93.2% |
| setup.controller | 100.0% | 100.0% | 0.0% | 0.0% |
| setup.dto | 100.0% | 100.0% | 0.0% | 0.0% |
| setup.service | 100.0% | 100.0% | 100.0% | 100.0% |
| share.controller | 100.0% | 100.0% | 0.0% | 0.0% |
| share.dto | 100.0% | 100.0% | 0.0% | 0.0% |
| share.service | 100.0% | 100.0% | 92.9% | 92.9% |
| user.controller | 100.0% | 100.0% | 0.0% | 0.0% |
| user.domain | 96.7% | 96.7% | 68.8% | 68.8% |
| user.dto | 100.0% | 100.0% | 0.0% | 0.0% |
| user.service | 100.0% | 100.0% | 100.0% | 100.0% |
| **Total** | 95.6% | 96.0% | 79.4% | 79.8% |

Branch 0% rows are packages with no conditional branches (controllers, DTOs); JaCoCo counts none there.

## What is covered

Auth and refresh-token rotation, invitation accept, the last-admin rule, assignment strategies,
presence/availability, inbox send and receive reconciliation, product share and stock movements all sit at
or above 90% line coverage in their service packages. Every exception handler has a status/body check
(`GlobalExceptionHandler` and each `<Feature>ExceptionHandler`, including `MetaExceptionHandlerTests`).

## Known remaining gaps

- Unreachable defensive catches (`NoSuchAlgorithmException`, `GeneralSecurityException`) in
  `InvitationTokenGenerator`, `TokenCipher` and `OAuthStateSigner`.
- Network adapters (`GraphHistoryClient`, `GraphMessagingClient`, `HttpMediaDownloader`, `MaxMindIpLocator`)
  are tested against stubs; their raw transport code is not.
- Entity `equals`/`hashCode` branches in domain packages, which explains their lower branch figures.
