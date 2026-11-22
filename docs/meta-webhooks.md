# Meta webhooks (design for #13)

Meta delivers new messages to one callback URL per Meta app. Two products use it: Messenger (object `page`) and Instagram (object `instagram`). This note fixes how Connecta receives those events. #45 implements reception; #46 implements processing.

## Endpoints

| Method | Path | Access | Behaviour |
|---|---|---|---|
| GET | `/api/meta/webhooks` | public (Meta's handshake) | Subscribe check: `hub.mode=subscribe` and `hub.verify_token` equal to `app.meta.webhook.verify-token`. Returns `hub.challenge` as text, `403` otherwise. A blank verify token always refuses. |
| POST | `/api/meta/webhooks` | public (the signature is the check) | Verifies `X-Hub-Signature-256` over the raw body, stores the event, returns `200` fast so Meta does not retry. |

## Signature

- Header `X-Hub-Signature-256: sha256=<hex>`, HMAC-SHA256 over the raw request body.
- Key by object: `page` uses `app.meta.app-secret`; `instagram` uses `app.meta.instagram.app-secret`. Each event is checked against the secret of its own product, so one app's secret cannot validate the other's events.
- Compare in constant time. A missing header, another algorithm, or a mismatch is `401`.
- A missing secret for the object is `503` (Meta not configured). The endpoint never accepts an unsigned event.
- The body is read as raw text, before JSON parsing, because the signature covers the exact bytes.

## Storage

Table `meta_webhook_events`: one row per verified delivery, holding `object` (`PAGE` or `INSTAGRAM`), the raw JSON `payload`, `received_at`, and `processed_at` (null until #46 handles it). Rows are stored only after the signature passes. Other objects (for example WhatsApp) are acknowledged with `200` and not stored.

Routing to a tenant is not done here. The payload names a page or Instagram account id; #46 maps that id to an organization through the connected-page and connected-account tables. Until then no tenant data is read or written by reception.

## Processing (META-06)

After a delivery is stored, `WebhookEventProcessor` handles it in the same transaction:

1. For each `entry`, the id is a Page id (`page`) or an Instagram account id (`instagram`). Connected organizations are looked up in the connection tables; an id no organization connected is skipped.
2. `MessagingEventParser` turns each messaging event into a `NormalizedMessage` (product, Meta message id, customer id, direction, text or attachment label, time). Echoes are outbound. Events without a message id or any content are skipped.
3. Each message is delivered once per connected organization: if `meta_processed_messages` already has it, it is skipped; otherwise the row is saved and a `MessageReceivedEvent` is published.
4. The event is marked processed.

A failure while processing rolls the transaction back, so the event is not stored and Meta retries the delivery. Nothing is swallowed.

## Failures

| Case | Response |
|---|---|
| GET with wrong or missing verify token | `403` |
| POST with missing or bad signature | `401` |
| POST with invalid JSON (after a valid signature) | `400` |
| Missing app secret or verify token | `503` (verify token blank: `403`, see above) |

Bodies are generic problem details, as for the other Meta endpoints.

## Pattern

Template Method for webhook intake: a fixed skeleton of verify, parse, dedupe, persist. #45 implements verify and persist. #46 adds the per-channel parse step and dedupe, without changing the skeleton.

## Open points for review

- Retries: Meta resends an event that got no `200`. Duplicates are handled in #46 (the Meta message id is unique per organization), so reception stores every verified delivery.
- Retention of raw payloads is not decided here; they are kept until processed, and a cleanup job is a later issue.
