# Design patterns

One row per pattern in use. Add a row (pattern, classes, reason) whenever a feature adds one.

| Pattern | Classes | Why |
|---|---|---|
| Repository | `user.repository.UserRepository`, `user.repository.RoleRepository`, `auth.repository.RefreshTokenRepository`, `org.repository.OrganizationRepository`, `org.repository.MembershipRepository`, `product.repository.ProductCategoryRepository`, `product.repository.ProductRepository` | All persistence goes through Spring Data repositories; services never write SQL. Product queries take the organization id, so tenant data is always filtered. |
| DTO + mapper | `auth.dto.UserResponse.from(User)`, `TokenResponse`, request records (`LoginRequest`, `RegisterRequest`, `RefreshRequest`) | Entities never reach JSON; a static `from(entity)` factory keeps the mapping in one place. |
| Chain of Responsibility | Spring Security filter chain configured in `config.SecurityConfig` (bearer-token filter, access handlers) | Each filter handles or passes on the request, so authentication and authorization stay separate from controllers. |
| DTO + mapper (paged) | `common.dto.PageResponse.from(Page, mapper)`, `org.dto.OrganizationResponse.from(Membership)` | Every list returns `{content, page, size, totalElements}` built from one mapper, so pages never expose entities. |
| Template Method (planned) | Webhook intake (M3+) | Shared verify, parse, dedupe, persist skeleton with a per-channel parsing step. |
| Strategy (planned) | `AssignmentStrategy` (M5+) | Round-robin, least-loaded and manual assignment behind one interface. |
| Adapter (planned) | `ChannelClient` (M4+) | One adapter per Meta channel, so the inbox never sees Meta's API. |
| Facade | `org.service.TenantAccessService` (`requireMembership`, `requireRole`, `hasAtLeast`) | One entry point for every tenant check, so features never touch `MembershipRepository` or the role order directly. |
| Factory Method (lightweight) | `org.service.InvitationTokenGenerator.issue()` returns `Issued(raw, hash)` | One call makes the random token and its SHA-256 hash together, so the raw token is handed out once and only the hash is stored. One product, so no abstract factory. |
| Observer | `org.service.InvitationCreatedEvent` published by `InvitationService.create` through `ApplicationEventPublisher`; `mail.service.InvitationMailListener` (`@TransactionalEventListener(AFTER_COMMIT)`) | Creating an invitation announces it without the service knowing who reacts. The listener emails the link only after the invitation is committed, so a rollback sends nothing. |
| Strategy | `mail.service.InvitationMailer` with `SmtpInvitationMailer` and `NoopInvitationMailer`, chosen once in `config.MailConfig` | The listener depends on one interface; SMTP is a variant, "not configured" is another, so no `if (smtp)` branch lives in the listener. Each variant has its own test. |
| Facade (signup flow) | `org.service.InvitationSignupService` (`preview`, `acceptAndSignUp`) | One call for the signed-out invitation flow: find the invitation, create the account, join the organization and start a session, all in one transaction. |
| Atomic claim | `setup.repository.SystemSetupRepository.claim` (`INSERT ... ON CONFLICT DO NOTHING` on the single-row `system_setup`) | Two first-run setups arriving together cannot both create an owner: the database picks the winner. |
| DTO + mapper (product categories) | `product.dto.ProductCategoryResponse.from(ProductCategory)`, request record `ProductCategoryRequest` | Category responses carry the organization id, never the `Organization` entity. |
| DTO + mapper (members) | `org.dto.MemberResponse.from(Membership)`, request record `UpdateMemberRoleRequest` | The member list shows user name, email and role without exposing the `User` entity or its password hash. |
| Adapter + Strategy (product images) | `product.service.ProductImageStorage` with `R2ProductImageStorage` (adapts the S3 client to Cloudflare R2) and `LocalProductImageStorage` (in-memory); chosen in `config.ProductImageStorageConfig` | Callers depend on one small interface and never see the S3 SDK. R2 is the adapter for the outside system; the in-memory store is the second variant for dev and tests. |
| Factory Method (product images) | `config.ProductImageStorageConfig.productImageStorage(R2Properties)` | Picks the variant once at startup from configuration; prod fails fast when R2 is required and missing. |
| DTO + mapper (products) | `product.dto.ProductResponse.from(Product)`, request records `CreateProductRequest` and `UpdateProductRequest` | Create and update have separate request records; the response flattens the optional category to its id and name and never exposes the entity. |
