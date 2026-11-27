import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import Avatar from "@/components/avatar";
import AssignmentPanel from "@/components/assignment-panel";
import CompactAssignment from "@/components/compact-assignment";
import ContactDetailsPane from "@/components/contact-details-pane";
import InboxListPane from "@/components/inbox-list-pane";
import InboxShell from "@/components/inbox-shell";
import MarkReadOnOpen from "@/components/mark-read-on-open";
import MessageList from "@/components/message-list";
import OrgActionButton from "@/components/org-action-button";
import RealtimeInboxRefresher from "@/components/realtime-inbox-refresher";
import ReplyComposer from "@/components/reply-composer";
import {
  previewProductShareAction,
  searchShareProductsAction,
  sendProductShareAction,
} from "@/lib/product-share-actions";
import { Badge } from "@/components/ui/badge";
import { assignConversationAction } from "@/lib/assignment-actions";
import { assigneeBadgeLabel } from "@/lib/assignment-view";
import { listAssignments, listAvailability } from "@/lib/assignment-api";
import { MAX_PAGE_SIZE } from "@/lib/org-api";
import { section } from "@/lib/inventory-view";
import {
  markConversationUnreadAction,
  sendAttachmentAction,
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
import { loadOrganization, loadSession, notFoundOnMissing } from "../../data";
import { loadInboxList } from "../list-data";

const HISTORY_SIZE = 10;

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
  // Only an admin may list the team and the history, so only then are those requests made; they run in
  // parallel with the rest, and each section fails on its own.
  const admin = loadOrganization(id).then(
    (organization) => organization.callerRole === "ADMIN",
  );
  const adminData = admin.then((isAdmin) =>
    isAdmin
      ? Promise.all([
          section(listAvailability(accessToken, id, 0, MAX_PAGE_SIZE)),
          section(
            listAssignments(accessToken, id, conversationId, 0, HISTORY_SIZE),
          ),
        ])
      : null,
  );
  const [list, conversation, messages, team] = await Promise.all([
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
    adminData,
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
      details={
        <ContactDetailsPane
          conversation={conversation}
          assignmentControl={
            team && (
              <AssignmentPanel
                action={assignConversationAction.bind(null, id, conversationId)}
                currentId={conversation.assignee?.membershipId ?? null}
                members={team[0].ok ? team[0].value.content : null}
                history={team[1].ok ? team[1].value : null}
              />
            )
          }
        />
      }
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
            <p
              aria-live="polite"
              className="flex flex-wrap items-center gap-x-2 gap-y-1"
            >
              <Badge>{INBOX_CHANNEL_LABELS[conversation.channel]}</Badge>
              <Badge tone={closed ? "neutral" : "accent"}>
                {closed ? "Closed" : "Open"}
              </Badge>
              <Badge>
                {conversation.assignee
                  ? assigneeBadgeLabel(
                      conversation.assignee,
                      team?.[0].ok ? team[0].value.content : null,
                    )
                  : "Unassigned"}
              </Badge>
            </p>
          </div>
        </div>
        {team?.[0].ok && (
          <CompactAssignment
            action={assignConversationAction.bind(null, id, conversationId)}
            currentId={conversation.assignee?.membershipId ?? null}
            members={team[0].value.content}
          />
        )}
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
            appName={INBOX_CHANNEL_LABELS[conversation.channel]}
          />
        </div>
      </div>

      <div className="px-4 pt-1 pb-4">
        <div className="mx-auto w-full max-w-[680px]">
          <ReplyComposer
            action={sendMessageAction.bind(null, id, conversationId)}
            attachAction={sendAttachmentAction.bind(null, id, conversationId)}
            productShare={{
              search: searchShareProductsAction.bind(null, id, conversationId),
              preview: previewProductShareAction.bind(null, id, conversationId),
              send: sendProductShareAction.bind(null, id, conversationId),
            }}
            recipient={`${participantsLabel(conversation.participants)} on ${INBOX_CHANNEL_LABELS[conversation.channel]}`}
          />
        </div>
      </div>
    </InboxShell>
  );
}
