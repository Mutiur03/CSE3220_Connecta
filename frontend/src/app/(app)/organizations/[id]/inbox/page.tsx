import type { Metadata } from "next";
import { InboxIcon } from "@/components/icons";
import InboxListPane from "@/components/inbox-list-pane";
import InboxShell from "@/components/inbox-shell";
import RealtimeInboxRefresher from "@/components/realtime-inbox-refresher";
import { loadSession } from "../data";
import { loadInboxList } from "./list-data";

type RouteParams = Promise<{ id: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "Inbox" };

export default async function InboxPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const query = await searchParams;
  const { accessToken } = await loadSession(id);
  const list = await loadInboxList(accessToken, id, query);

  return (
    <InboxShell
      threadOpen={false}
      list={
        <InboxListPane orgId={id} headingLevel={1} now={new Date()} {...list} />
      }
    >
      <RealtimeInboxRefresher orgId={id} />
      <div className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
        <span className="grid size-10 place-items-center rounded-control border border-line text-secondary">
          <InboxIcon />
        </span>
        <p className="font-medium">Pick a conversation</p>
        <p className="max-w-xs text-sm text-secondary">
          Choose one from the list to read the history and reply.
        </p>
      </div>
    </InboxShell>
  );
}
