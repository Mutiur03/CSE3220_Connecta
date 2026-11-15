# Design patterns

One row per pattern in use. Add a row (pattern, classes, reason) whenever a feature adds one.

| Pattern | Classes | Why |
|---|---|---|
| Repository | `user.repository.UserRepository`, `user.repository.RoleRepository`, `auth.repository.RefreshTokenRepository`, `org.repository.OrganizationRepository`, `org.repository.MembershipRepository` | All persistence goes through Spring Data repositories; services never write SQL. |
| DTO + mapper | `auth.dto.UserResponse.from(User)`, `TokenResponse`, request records (`LoginRequest`, `RegisterRequest`, `RefreshRequest`) | Entities never reach JSON; a static `from(entity)` factory keeps the mapping in one place. |
| Chain of Responsibility | Spring Security filter chain configured in `config.SecurityConfig` (bearer-token filter, access handlers) | Each filter handles or passes on the request, so authentication and authorization stay separate from controllers. |
| Template Method (planned) | Webhook intake (M3+) | Shared verify, parse, dedupe, persist skeleton with a per-channel parsing step. |
| Strategy (planned) | `AssignmentStrategy` (M5+) | Round-robin, least-loaded and manual assignment behind one interface. |
| Adapter (planned) | `ChannelClient` (M4+) | One adapter per Meta channel, so the inbox never sees Meta's API. |
