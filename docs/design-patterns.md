# Design patterns

One row per pattern in use. Add a row (pattern, classes, reason) whenever a feature adds one.

| Pattern | Classes | Why |
|---|---|---|
| Repository | `user.repository.UserRepository`, `user.repository.RoleRepository`, `auth.repository.RefreshTokenRepository`, `org.repository.OrganizationRepository`, `org.repository.MembershipRepository` | All persistence goes through Spring Data repositories; services never write SQL. |
| DTO + mapper | `auth.dto.UserResponse.from(User)`, `TokenResponse`, request records (`LoginRequest`, `RegisterRequest`, `RefreshRequest`) | Entities never reach JSON; a static `from(entity)` factory keeps the mapping in one place. |
| Chain of Responsibility | Spring Security filter chain configured in `config.SecurityConfig` (bearer-token filter, access handlers) | Each filter handles or passes on the request, so authentication and authorization stay separate from controllers. |
| DTO + mapper (paged) | `common.dto.PageResponse.from(Page, mapper)`, `org.dto.OrganizationResponse.from(Organization)` | Every list returns `{content, page, size, totalElements}` built from one mapper, so pages never expose entities. |
| Template Method (planned) | Webhook intake (M3+) | Shared verify, parse, dedupe, persist skeleton with a per-channel parsing step. |
| Strategy (planned) | `AssignmentStrategy` (M5+) | Round-robin, least-loaded and manual assignment behind one interface. |
| Adapter (planned) | `ChannelClient` (M4+) | One adapter per Meta channel, so the inbox never sees Meta's API. |
| Facade | `org.service.TenantAccessService` (`requireMembership`, `requireRole`, `hasAtLeast`) | One entry point for every tenant check, so features never touch `MembershipRepository` or the role order directly. |
