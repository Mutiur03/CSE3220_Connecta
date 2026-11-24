import type { Metadata } from "next";
import Link from "next/link";
import MessageList from "@/components/message-list";
import OrgActionButton from "@/components/org-action-button";
import RealtimeInboxRefresher from "@/components/realtime-inbox-refresher";
import { Badge } from "@/components/ui/badge";
import { setConversationStatusAction } from "@/lib/inbox-actions";
import { getConversation, listMessages } from "@/lib/inbox-api";
import {
  INBOX_CHANNEL_LABELS,
  MESSAGES_PAGE_SIZE,
  nextStatus,
  participantsLabel,
  threadHref,
} from "@/lib/inbox-view";
import { parsePageParam } from "@/lib/org-pagination";
import { isUuid } from "@/lib/org-validation";
import { notFound } from "next/navigation";
import { loadSession, notFoundOnMissing } from "../../data";

type RouteParams = Promise<{ id: string; conversationId: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "Conversation" };

export default async function ConversationPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id, conversationId } = await params;
  if (!isUuid(id) || !isUuid(conversationId)) notFound();
  const page = parsePageParam((await searchParams).page);
  const { accessToken } = await loadSession(id);
  const [conversation, messages] = await Promise.all([
    notFoundOnMissing(getConversation(accessToken, id, conversationId)),
    notFoundOnMissing(
      listMessages(accessToken, id, conversationId, page, MESSAGES_PAGE_SIZE),
    ),
  ]);

  const threadPath = `/organizations/${id}/inbox/${conversationId}`;
  const closed = conversation.status === "CLOSED";
  const hasOlder = (page + 1) * messages.size < messages.totalElements;

  return (
    <>
      <RealtimeInboxRefresher orgId={id} conversationId={conversationId} />
      <header className="flex flex-col gap-3">
        <Link
          href={`/organizations/${id}/inbox`}
          className="self-start text-sm text-secondary underline-offset-4 hover:underline"
        >
          Back to inbox
        </Link>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-2">
            <h1 className="break-words text-display font-medium">
              {participantsLabel(conversation.participants)}
            </h1>
            <p className="flex items-center gap-2">
              <Badge>{INBOX_CHANNEL_LABELS[conversation.channel]}</Badge>
              <Badge tone={closed ? "neutral" : "accent"}>
                {closed ? "Closed" : "Open"}
              </Badge>
            </p>
          </div>
          <OrgActionButton
            key={conversation.status}
            action={setConversationStatusAction.bind(
              null,
              id,
              conversationId,
              nextStatus(conversation.status),
            )}
            label={closed ? "Reopen conversation" : "Close conversation"}
            pendingLabel={closed ? "Reopening…" : "Closing…"}
          />
        </div>
      </header>

      <section aria-label="Messages">
        <MessageList
          messages={messages.content}
          olderHref={hasOlder ? threadHref(threadPath, page + 1) : undefined}
          newerHref={page > 0 ? threadHref(threadPath, page - 1) : undefined}
        />
      </section>
    </>
  );
}
