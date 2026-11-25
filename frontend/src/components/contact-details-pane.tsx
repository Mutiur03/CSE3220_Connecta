import type { ConversationDetail } from "@/lib/inbox-api";
import { formatDate, formatDateTime } from "@/lib/format";
import { INBOX_CHANNEL_LABELS, participantLabel } from "@/lib/inbox-view";
import Avatar from "./avatar";

type Props = { conversation: ConversationDetail };

/**
 * The right pane from 1280px: who the customer is and where the conversation stands. Rows and a definition
 * list, no cards. Hidden below that width; the thread header already carries the name, channel and status.
 */
export default function ContactDetailsPane({ conversation }: Props) {
  const [customer, ...others] = conversation.participants;
  const name = customer?.displayName ?? null;
  const facts: { term: string; value: React.ReactNode }[] = [
    { term: "Channel", value: INBOX_CHANNEL_LABELS[conversation.channel] },
    {
      term: "Status",
      value: conversation.status === "CLOSED" ? "Closed" : "Open",
    },
    {
      term: "First contact",
      value: (
        <time dateTime={conversation.createdAt}>
          {formatDate(conversation.createdAt)}
        </time>
      ),
    },
  ];
  if (conversation.lastMessageAt) {
    facts.push({
      term: "Last message",
      value: (
        <time dateTime={conversation.lastMessageAt}>
          {formatDateTime(conversation.lastMessageAt)}
        </time>
      ),
    });
  }
  if (others.length > 0) {
    facts.push({
      term: "Also in this conversation",
      value: others
        .map((person) => participantLabel(person.displayName))
        .join(", "),
    });
  }

  return (
    <aside
      aria-label="Contact details"
      className="hidden w-[300px] shrink-0 flex-col overflow-y-auto border-l border-line bg-surface xl:flex"
    >
      <div className="flex flex-col items-center gap-3 border-b border-line px-4 py-6 text-center">
        <Avatar name={name} src={customer?.avatarUrl ?? null} size="md" />
        <p className="text-title font-semibold break-words">
          {participantLabel(name)}
        </p>
      </div>
      <dl className="flex flex-col px-4 py-2">
        {facts.map((fact) => (
          <div
            key={fact.term}
            className="flex flex-col gap-0.5 border-b border-line-subtle py-3 last:border-b-0"
          >
            <dt className="text-caption text-secondary">{fact.term}</dt>
            <dd className="text-sm">{fact.value}</dd>
          </div>
        ))}
      </dl>
    </aside>
  );
}
