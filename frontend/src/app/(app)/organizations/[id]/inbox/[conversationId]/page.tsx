import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import Avatar from "@/components/avatar";
import ContactDetailsPane from "@/components/contact-details-pane";
import InboxListPane from "@/components/inbox-list-pane";
import InboxShell from "@/components/inbox-shell";
import MarkReadOnOpen from "@/components/mark-read-on-open";
import MessageList from "@/components/message-list";
import OrgActionButton from "@/components/org-action-button";
import RealtimeInboxRefresher from "@/components/realtime-inbox-refresher";
import ReplyComposer from "@/components/reply-composer";
import { Badge } from "@/components/ui/badge";
import {
  markConversationUnreadAction,
  sendMessageAction,
  setConversationStatusAction,
} from "@/lib/inbox-actions";
import { getConversation, listMessages } from "@/lib/inbox-api";
import {
  INBOX_CHANNEL_LABELS,
  MESSAGES_PAGE_SIZE,
  inboxHref,
  nextStatus,
  participantsLabel,
  threadHref,
} from "@/lib/inbox-view";
import { parsePageParam } from "@/lib/org-pagination";
import { isUuid } from "@/lib/org-validation";
import { loadSession, notFoundOnMissing } from "../../data";
import { loadInboxList } from "../list-data";

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
  const query = await searchParams;
  const history = parsePageParam(query.history);
  const { accessToken } = await loadSession(id);
  const [list, conversation, messages] = await Promise.all([
    loadInboxList(accessToken, id, query),
    notFoundOnMissing(getConversation(accessToken, id, conversationId)),
    notFoundOnMissing(
      listMessages(
        accessToken,
        id,
        conversationId,
        history,
        MESSAGES_PAGE_SIZE,
      ),
    ),
  ]);

  const threadPath = `/organizations/${id}/inbox/${conversationId}`;
  const historyLink = (target: number) =>
    threadHref(threadPath, list.filters, list.page, target);
  const closed = conversation.status === "CLOSED";
  const hasOlder = (history + 1) * messages.size < messages.totalElements;
  const customer = conversation.participants[0];

  return (
    <InboxShell
      threadOpen
      details={<ContactDetailsPane conversation={conversation} />}
      list={
        <InboxListPane
          orgId={id}
          headingLevel={2}
          now={new Date()}
          selectedId={conversationId}
          {...list}
        />
      }
    >
      <RealtimeInboxRefresher orgId={id} />
      <MarkReadOnOpen
        orgId={id}
        conversationId={conversationId}
        unread={conversation.unread}
      />
      <header className="flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-line px-4 py-3">
        <Link
          href={inboxHref(
            `/organizations/${id}/inbox`,
            list.filters,
            list.page,
          )}
          className="inline-flex min-h-10 basis-full items-center text-sm text-secondary underline-offset-4 hover:text-primary hover:underline lg:hidden"
        >
          Back to inbox
        </Link>
        <div className="flex min-w-0 flex-1 basis-48 items-center gap-3">
          <Avatar
            name={customer?.displayName ?? null}
            src={customer?.avatarUrl ?? null}
          />
          <div className="flex min-w-0 flex-col">
            <h1 className="truncate text-title font-semibold">
              {participantsLabel(conversation.participants)}
            </h1>
            <p aria-live="polite" className="flex items-center gap-2">
              <Badge>{INBOX_CHANNEL_LABELS[conversation.channel]}</Badge>
              <Badge tone={closed ? "neutral" : "accent"}>
                {closed ? "Closed" : "Open"}
              </Badge>
            </p>
          </div>
        </div>
        <OrgActionButton
          action={markConversationUnreadAction.bind(null, id, conversationId)}
          label="Mark unread"
          pendingLabel="Marking…"
        />
        {/* data-shortcut lets the E key press this button. */}
        <span data-shortcut="status" className="contents">
          <OrgActionButton
            key={conversation.status}
            action={setConversationStatusAction.bind(
              null,
              id,
              conversationId,
              nextStatus(conversation.status),
            )}
            label={closed ? "Reopen" : "Close"}
            pendingLabel={closed ? "Reopening…" : "Closing…"}
          />
        </span>
      </header>

      {/* column-reverse starts the scroll at the newest message, with no script. */}
      <div className="flex min-h-0 flex-1 flex-col-reverse overflow-y-auto overscroll-contain">
        <div className="mx-auto w-full max-w-[680px] px-4 py-6">
          <MessageList
            messages={messages.content}
            now={new Date()}
            olderHref={hasOlder ? historyLink(history + 1) : undefined}
            newerHref={history > 0 ? historyLink(history - 1) : undefined}
          />
        </div>
      </div>

      <div className="px-4 pt-1 pb-4">
        <div className="mx-auto w-full max-w-[680px]">
          <ReplyComposer
            action={sendMessageAction.bind(null, id, conversationId)}
            recipient={`${participantsLabel(conversation.participants)} on ${INBOX_CHANNEL_LABELS[conversation.channel]}`}
          />
        </div>
      </div>
    </InboxShell>
  );
}
