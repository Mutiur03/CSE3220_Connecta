import { getOwnAvailability } from "@/lib/assignment-api";
import { listConversations } from "@/lib/inbox-api";
import { parseInboxFilters } from "@/lib/inbox-view";
import { ORG_PAGE_SIZE, parsePageParam } from "@/lib/org-pagination";
import { notFoundOnMissing } from "../data";

type SearchParams = Record<string, string | string[] | undefined>;

/** The list pane's data for both inbox routes: the filters and list page come from the URL. */
export async function loadInboxList(
  accessToken: string,
  orgId: string,
  query: SearchParams,
) {
  const page = parsePageParam(query.page);
  const filters = parseInboxFilters(query);
  const [conversations, availability] = await Promise.all([
    notFoundOnMissing(
      listConversations(accessToken, orgId, filters, page, ORG_PAGE_SIZE),
    ),
    // The status control is a convenience: when it cannot load, the list still renders without it.
    getOwnAvailability(accessToken, orgId).catch(() => null),
  ]);
  return { page, filters, conversations, availability };
}

export type InboxListData = Awaited<ReturnType<typeof loadInboxList>>;
