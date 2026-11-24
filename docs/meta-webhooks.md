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
3. Each message is delivered once per connected organization: if `meta_processed_messages` already has it, it is skipped; otherwise the row is saved and `IncomingMessageService.receive` stores it in the inbox: the customer (`participants`) and the conversation (organization, channel, thread = customer id) are found or created (a new conversation records the connected Page or Instagram account the webhook arrived on in `conversations.meta_page_id` / `instagram_account_id`; later messages never change it), the message is saved (an id the organization already has in `messages` is skipped), and an `inbox.service.MessageReceivedEvent` is published.

### Customer name lookup
The webhook carries only the sender id, so a new customer starts without a display name. After the webhook transaction commits, `CustomerProfileListener` (an `AFTER_COMMIT` listener on `MessageReceivedEvent`, run with `@Async` on the bounded `profileLookupExecutor`) asks `ParticipantProfileService` whether the conversation's customer still has no name; if so, `CustomerProfileClientFactory` picks the adapter for the channel and it calls Graph with the connected account's decrypted token: Messenger `GET /{psid}?fields=name` (Page token as `access_token`), Instagram `GET /{igsid}?fields=name,username` (Bearer token; the username is used when there is no name). The name is stored in a second short transaction (`storeName`, which never overwrites an existing name) and a `participant.updated` realtime frame (`{"type":"participant.updated","conversationId":"..."}`) makes open inboxes refresh. The HTTP call holds no database connection and the webhook never waits for it. Any failure (Graph error such as a missing permission or consent, timeout, no name, account gone) is logged at info/warn with the status only, never the token or the customer, and the lookup is simply tried again on the customer's next message. Tuning: `app.meta.profile-lookup.timeout` (3s), `pool-size` (2), `queue-capacity` (100; a full queue drops the lookup).
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

Outgoing replies (`POST .../messages`) are stored with Meta's returned message id, so the webhook echo of the same message is deduped by the organization's existing `externalMessageId` check and never creates a second row.

The send holds no database connection during the Meta call. `OutgoingMessageService.send` is not transactional: it loads the conversation (one short read transaction), calls Meta, then `OutgoingMessageRecorder.record` (a separate bean, so its `@Transactional` applies) stores the row in a second short transaction. If the echo already stored the message, that row is attributed to the sending agent. If the echo commits between the lookup and the save, the unique constraint on `(organization_id, external_message_id)` fires; the service catches that `DataIntegrityViolationException` and `attributeStored` attributes the stored row in a fresh transaction (any other violation is rethrown). Whenever attribution changes the sender of an echoed row, a `MessageReceivedEvent` is published again so the live push carries the sender name.

## Replying from the right account (INBOX-08)

A conversation points at the connected account it belongs to: `meta_page_id` (Messenger) or `instagram_account_id` (Instagram), both nullable foreign keys with `ON DELETE SET NULL` and a check that the one set matches the channel. `MessengerChannelClient` and `InstagramChannelClient` send with that account's token, looked up by id and organization. A conversation with no recorded account (rows from before the column whose organization had several accounts, or an account that was disconnected) falls back to the organization's account only when it has exactly one; with several the reply is refused with `422` ("not linked to a connected account"), and with none `422` "no connected account". The migration backfills conversations of organizations that have exactly one account of the channel.
