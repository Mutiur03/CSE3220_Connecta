# Analytics metrics

The contract for M9 (analytics and reporting). It fixes what each number means and the JSON each
endpoint returns, so the backend tasks (#72 conversations, #74 agents) and the dashboard (#75) can be
built in parallel against the same definitions. A change to a metric or a field changes this file in
the same PR.

## Common rules

| Rule | Value |
|---|---|
| Who | ADMIN of the organization (OWNER inherits). AGENT gets `403`. Another organization's id gets `404`. Stated twice: URL rule `/api/organizations/*/analytics/**` in `SecurityConfig` and `@PreAuthorize` plus `TenantAccessService.requireRole(..., ADMIN)` in the service. |
| Range | `from` and `to` are ISO dates (`2026-11-01`), both inclusive. Default: the last 30 days ending today. `from` after `to`, or a range over 366 days, is a `400`. |
| Time zone | `zone` is an IANA zone id (`Asia/Dhaka`), default `UTC`. Day boundaries and the daily buckets use it. An unknown zone is a `400`. The frontend sends the reader's zone. |
| Scope | Every query filters by `organization_id`. Nothing from another organization is counted. |
| Durations | Whole seconds (`long`). The frontend formats them. `null` when there is nothing to measure. |
| Empty range | Every count is `0`, every list is complete (days with no activity are present with `0`). Never a `404`. |

"In range" below means the timestamp falls between the start of `from` and the end of `to` in `zone`.

## Conversation metrics (#72)

`GET /api/organizations/{organizationId}/analytics/conversations?from=&to=&zone=`

| Metric | Definition | Source |
|---|---|---|
| Conversations started | Conversations whose first message (`min(messages.sent_at)`) is in range. Not `conversations.created_at`: the history import sets that to the import time, which would put months of chats on one day. | `messages` |
| Started per day | The same count, bucketed by day in `zone`. | `messages` |
| Started per channel | The same count, by `conversations.channel` (`INSTAGRAM`, `MESSENGER`). | `conversations` |
| Status now | Count of all the organization's conversations by current `status` (`OPEN`, `CLOSED`). A snapshot, not limited by the range. | `conversations` |
| Messages received | Inbound messages with `sent_at` in range. | `messages` |
| Messages sent | Outbound messages with `sent_at` in range, from Connecta or from Meta's own apps. | `messages` |
| First response time | For each conversation started in range: time from its first inbound message to the first outbound message after it. Reported as the median over conversations that got a reply. | `messages` |
| Unanswered | Conversations started in range with an inbound message and no outbound message after it. | `messages` |

```json
{
  "from": "2026-11-01",
  "to": "2026-11-28",
  "zone": "Asia/Dhaka",
  "started": 142,
  "startedPerDay": [{ "date": "2026-11-01", "count": 5 }],
  "startedPerChannel": [{ "channel": "INSTAGRAM", "count": 90 }, { "channel": "MESSENGER", "count": 52 }],
  "statusNow": { "open": 37, "closed": 410 },
  "messagesReceived": 1210,
  "messagesSent": 980,
  "medianFirstResponseSeconds": 540,
  "unanswered": 8
}
```

## Agent metrics (#74)

`GET /api/organizations/{organizationId}/analytics/agents?from=&to=&zone=&page=&size=`

One row per current member of the organization (ADMIN and AGENT), sorted by name (`users.full_name`). Paginated
like every list (`page`, `size` default 20, max 100), so the response is `{content, page, size,
totalElements}` plus the range fields.

| Metric | Definition | Source |
|---|---|---|
| Assigned | Assignment rows handing a conversation to this member (`to_membership_id`) with `created_at` in range, automatic and manual. | `conversation_assignments` |
| Resolved | Conversations this member closed (`closed_by_user_id`) with `closed_at` in range. | `conversations` (new columns, below) |
| Open now | Open conversations currently assigned to this member. A snapshot, not limited by the range. Same number as `ConversationWorkloadService`. | `conversations` |
| Replies sent | Outbound messages sent through Connecta by this member's user (`sent_by_user_id`) with `sent_at` in range. Replies sent from Meta's own apps have no user and are not counted for anyone. | `messages` |
| Available time | Seconds this member was AVAILABLE inside the range: the overlap of each `availability_intervals` row with the range, an open row counted up to now. | `availability_intervals` |
| Median first response | Over conversations started in range whose first outbound reply came from this member: median time from first inbound to that reply. `null` when there are none. | `messages` |

```json
{
  "from": "2026-11-01",
  "to": "2026-11-28",
  "zone": "Asia/Dhaka",
  "content": [
    {
      "membershipId": "6b1c...",
      "name": "Aavas Roy",
      "role": "AGENT",
      "assigned": 48,
      "resolved": 41,
      "openNow": 6,
      "repliesSent": 312,
      "availableSeconds": 302400,
      "medianFirstResponseSeconds": 420
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 4
}
```

### Data #74 adds

Nothing records when a conversation was closed or by whom, so "Resolved" cannot be counted today.
#74 adds a migration:

```sql
ALTER TABLE conversations
    ADD COLUMN closed_at         TIMESTAMPTZ,
    ADD COLUMN closed_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL;
```

with an index on `(organization_id, closed_at)` and one on `closed_by_user_id`. `changeStatus` sets both
when a conversation closes and clears both when it reopens, so a reopened conversation is not counted
as resolved. Conversations closed before the migration have `closed_at` null and are not counted;
reports start counting from the deploy.

## Out of scope

Kept out on purpose, add a task if the demo needs one:

- Response time on every message (only the first response is measured).
- Per-hour heatmaps, CSV export, comparison with the previous period.
- Resolution history across reopen cycles (only the latest close counts).
- Analytics for AGENT users about themselves.

## Ownership

| Part | Task | Account |
|---|---|---|
| This contract | #71 | Mutiur03 |
| Shared `analytics` package: range parameters and their `400`s, `AnalyticsExceptionHandler`, the `SecurityConfig` rule; then the conversation endpoint | #72 | Aavas-99 |
| `closed_at` migration, then the agent endpoint | #74 | bipro96 |
| Dashboard: rail entry, range filter, charts, agent table | #75 | bipro96 |
