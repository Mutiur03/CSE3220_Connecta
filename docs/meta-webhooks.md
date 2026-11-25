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
2. `MessagingEventParser` turns each messaging event into a `NormalizedMessage` (product, Meta message id, customer id, direction, text or attachment label, time, attachments). Echoes are outbound. Events without a message id or any content are skipped.
   - Attachments: each `message.attachments[]` entry of type `image`, `video` (also Instagram `ig_reel` / `reel`), `audio` or `file` becomes a `NormalizedAttachment` (kind `IMAGE`, `VIDEO`, `AUDIO`, `FILE`, `payload.url`, `payload.name` when present). Only absolute `http`/`https` URLs are kept. Any other type (`location`, `fallback`, `share`, `story_mention`, ...) or an entry without a usable URL is dropped, never an error. A message without text takes the label of its first attachment as text: `[Photo]`, `[Video]`, `[Voice message]`, otherwise `[Attachment]`.
3. Each message is delivered once per connected organization: if `meta_processed_messages` already has it, it is skipped; otherwise the row is saved and `IncomingMessageService.receive` stores it in the inbox: the customer (`participants`) and the conversation (organization, channel, thread = customer id) are found or created (a new conversation records the connected Page or Instagram account the webhook arrived on in `conversations.meta_page_id` / `instagram_account_id`; later messages never change it), the message and its attachments (`message_attachments`, same organization, same transaction) are saved (an id the organization already has in `messages` is skipped, attachments included), and an `inbox.service.MessageReceivedEvent` is published.

4. The event is marked processed.

A failure while processing rolls the transaction back, so the event is not stored and Meta retries the delivery. Nothing is swallowed.

### Keeping a copy of incoming media
Meta's media URLs expire, so each attachment that arrived from Meta (webhook or history import) is copied once into `stored_files` and then served from `PUBLIC_BASE_URL/api/files/{id}`. The attachment row keeps the Meta link in `source_url`, points `stored_file_id` at the copy, and its `url` becomes our public URL, so `MessageResponse.attachments[].url` is the Meta URL until the copy exists and ours after (no client change).

- When: `inbox.service.AttachmentMirrorListener` reacts to `MessageReceivedEvent` after commit (`@TransactionalEventListener(AFTER_COMMIT)`) on the bounded `attachmentMirrorExecutor`, never inside the webhook transaction. After a history import (`HistoryImportedEvent`) it copies one batch (`mirror-batch-size`) of that organization's pending attachments; `AttachmentMirrorSweepJob` does the rest and every retry: every `mirror-sweep-delay`, one batch of attachments without a copy, younger than `mirror-max-age` (3 days), never tried first, then least recently tried. The sweep hands the batch to the same executor, so the scheduler thread never downloads.
- Download (Adapter): `MediaDownloader`, implemented by `HttpMediaDownloader` (JDK `HttpURLConnection`, connect and read timeouts `mirror-timeout`, no automatic redirects). SSRF guard on the URL and on every redirect hop (at most 3): `https` on the default port only; host equal to or under one of `mirror-allowed-host-suffixes` (`fbcdn.net`, `cdninstagram.com`, `fbsbx.com`, `facebook.com`); every address the host resolves to must be public (loopback, private, link-local, any-local, carrier-grade NAT, multicast and IPv6 unique-local are refused). A declared `Content-Length` over `mirror-max-bytes` (25 MB) is refused before reading, and the body is read up to the cap plus one byte and then aborted.
- Type: the bytes must be one of the upload allow-list types (`AttachmentType`, magic bytes) of the attachment's kind. The declared type wins when it matches; otherwise (a CDN's `application/octet-stream`, a voice note sent as `video/mp4`) the first allowed type of that kind whose signature matches is used. Anything else is not stored.
- Idempotence: a copy is claimed with one atomic update (`mirror_attempted_at`, only when no copy exists and nobody tried within `mirror-retry-after`), so one worker downloads. The save locks the row (`SELECT ... FOR UPDATE`) and re-checks `stored_file_id`, so two workers, two instances or a replay never store two files.
- Failure: the Meta URL stays, the reason is logged (`MediaDownloadFailure`, never the URL or a token) and the sweep retries after `mirror-retry-after` until the attachment is older than `mirror-max-age`.
- Tenant scoping: every query and write carries the attachment's organization, and the file is stored for that organization.
- Live update: a stored copy publishes `AttachmentMirroredEvent`; `InboxRealtimeListener` sends the existing `participant.updated` push for that conversation, so open threads reload.
- Skipped: attachments our agents sent (no `source_url`, their URL is already ours), and everything while `PUBLIC_BASE_URL` is blank.

### Customer name lookup
The webhook carries only the sender id, so a new customer starts without a display name. After the webhook transaction commits, `CustomerProfileListener` (an `AFTER_COMMIT` listener on `MessageReceivedEvent`, run with `@Async` on the bounded `profileLookupExecutor`) asks `ParticipantProfileService` whether the conversation's customer profile is due (no name, never looked up, or the last lookup is at least `picture-ttl` old; see below); if so, `CustomerProfileClientFactory` picks the adapter for the channel and it calls Graph with the connected account's decrypted token: Messenger `GET /{psid}?fields=name,profile_pic` (Page token as `Authorization: Bearer`), Instagram `GET /{igsid}?fields=name,username,profile_pic` (Bearer token; the username is used when there is no name). The result is stored in a second short transaction (`storeProfile`: the name is never overwritten, the picture is replaced by the newer one) and a `participant.updated` realtime frame (`{"type":"participant.updated","conversationId":"..."}`) makes open inboxes refresh. The HTTP call holds no database connection and the webhook never waits for it. Any failure (Graph error such as a missing permission or consent, timeout, no name, account gone) is logged at info/warn with the status only, never the token or the customer, and the lookup is simply tried again on the customer's next message. Tuning: `app.meta.profile-lookup.timeout` (3s), `pool-size` (2), `queue-capacity` (100; a full queue drops the lookup). A dropped lookup is logged at info (queue capacity only) and counted in the Micrometer counter `meta.profile.lookup.dropped`. On shutdown the executor finishes queued lookups, waiting up to `shutdown-timeout` (10s).

**Profile picture.** The customer's `profile_pic` URL is kept in `participants.profile_picture_url` (nullable `text`, `https` only, at most 2048 characters, also a CHECK constraint) with `profile_picture_fetched_at`. Meta picture URLs are signed and expire and people change pictures, so the stored link is refreshed: a lookup is due when the customer has no name, was never looked up, or `profile_picture_fetched_at` is at least `app.meta.profile-lookup.picture-ttl` (default `12h`) old, and it runs on the next inbound message. A lookup that finds no picture still stamps the time, so a customer without a picture is asked again only after the TTL, not on every message. A failed lookup (error, timeout, queue full) changes nothing: the old name and picture stay and the next message retries. A new picture link announces `participant.updated` like a new name. After every completed history import (even one that stored no new message, e.g. a reconnect) and once at startup (across all organizations, `import-refresh-limit` in total), `CustomerProfileListener` also looks up at most `app.meta.profile-lookup.import-refresh-limit` (default 20, `0` turns it off) customers of that organization never looked up, newest conversations first, in one task on the same bounded executor, so past conversations get pictures without a request storm. The API returns the link as `avatarUrl` (conversation list and detail). We do not download, proxy or cache the image: the browser loads the link directly (`<img>` with `referrerpolicy="no-referrer"`, not `next/image`, whose cache would keep a link that later dies) and an expired link falls back to the initials. Verification status: only exercised against stubs; the `profile_pic` field on live Messenger and Instagram profiles is not verified.

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

## Importing past conversations (INBOX-13)

Webhooks only deliver messages sent after the connection, so the history of a connected Page or Instagram account is read once through the Graph conversations API and stored through the same `IncomingMessageService` as a webhook message (so dedupe, account linking and tenant scoping live in one place). Nothing is exposed over HTTP: it starts by itself.

- Trigger (Observer): `MetaPageService` and `InstagramConnectService` publish `ChannelConnectedEvent` after saving the connection; `HistoryImportTrigger` hears it after commit and queues the import on a small bounded executor (`HistoryImportScheduler`). The connect request never waits for it and never fails because of it. At `ApplicationReadyEvent` the same trigger queues every connection whose `history_imported_at` is null (one sequential job). A full queue drops the job; the next start picks it up.
- Read (Adapter, Factory, Template Method): `ConversationHistoryClient` with `MessengerHistoryClient` (`GET graph.facebook.com/{version}/{page-id}/conversations?platform=messenger`) and `InstagramHistoryClient` (`graph.instagram.com/{version}/{ig-id}/conversations?platform=instagram`), chosen by `ConversationHistoryClientFactory`. Fields: `participants,messages.limit(N){id,message,from,created_time,attachments{mime_type,name,file_url,image_data{url},video_data{url}}}`. Each attachment is read as `image_data.url` (image), `video_data.url` (video), else `file_url` with its kind from `mime_type` (`image/*`, `video/*`, `audio/*`, anything else a file); `name` and `mime_type` are kept. Anything else, or a non-http(s) URL, is skipped. If Graph rejects these fields on the first request (HTTP 400, Graph code 100; permission and token errors are not retried), that request is sent once more with the plain fields `participants,messages.limit(N){id,message,from,created_time}`, a warning is logged without URL or token, and the import continues with labels only. The fallback is per import, never remembered. The token goes in the `Authorization: Bearer` header (never in a URL we log); every Messenger and Instagram send, profile and history client sets it through the one `GraphAuth` helper. `paging.next` (conversations and each conversation's nested `messages` edge) is followed only when the parsed link is `https` on the configured Graph host (no userinfo, default port); its embedded `access_token` is stripped before the request, so the token is never sent twice. A conversation's messages edge is followed until `messages-per-conversation` or the total cap is reached, then the import reports `truncated`. A paging link that repeats, or more than `max-graph-pages` Graph requests in one import, ends the paging (marked truncated, no error) so a looping Meta answer cannot hold a worker thread. The graph version is `META_GRAPH_API_VERSION`. Verification status: history is the one verified live (a Page token with Bearer against graph.facebook.com); Messenger send and profile, and everything on graph.instagram.com, are only exercised against stubs.
- Direction: a message sent by the Page or Instagram account itself is outbound, anything else inbound from the other participant. Conversations arrive newest first, messages newest first; each conversation is stored oldest message first so its last activity ends up right.
- Transactions: Meta is read page by page and every message is stored in its own short transaction (`HistoryImportService` is not transactional, `HistoryAccountStore` holds the short reads and the mark), so no connection is held across a Meta call. A Meta failure part-way keeps what was stored and leaves `history_imported_at` empty; a re-run continues because storing is idempotent. A guard allows one import per account at a time (in memory: single instance).
- Failures: a permission or fetch failure is remembered on the connection (`history_import_error` `PERMISSION` or `FETCH`, `history_import_failed_at`; never Meta's text or the token) and shown on the Integrations screen; the next successful run clears it. It stays pending, so a restart or reconnect retries.
- Realtime: imported messages publish no `MessageReceivedEvent`; one `HistoryImportedEvent` per import becomes one `inbox.refresh` push.
- What Meta does not return: only the newest messages per conversation within the limits; conversations older than Meta keeps; attachment files themselves (Meta's URL is stored and then copied, see "Keeping a copy of incoming media"); shares and story replies (kept as their label); people who blocked the Page. Needs `pages_messaging` / `instagram_manage_messages` granted to the connection. Permission and token errors (Graph codes 10, 190, 200 to 299) are logged by code only.
