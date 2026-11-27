import type {
  AssignmentRecord,
  MemberAvailability,
} from "@/lib/assignment-api";
import type { Page } from "@/lib/org-api";
import { formatDateTime } from "@/lib/format";
import { historyLine } from "@/lib/assignment-view";
import type { OrgActionState } from "@/lib/org-actions";
import AssignConversationForm from "./assign-conversation-form";
import { Alert } from "./ui/alert";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  currentId: string | null;
  /** Null when the member list could not be loaded. */
  members: MemberAvailability[] | null;
  /** Null when the history could not be loaded. */
  history: Page<AssignmentRecord> | null;
};

/** Admin only, in the details pane's Assignee row: change the assignee and read who held the conversation before. */
export default function AssignmentPanel({
  action,
  currentId,
  members,
  history,
}: Props) {
  return (
    <section aria-label="Assignment" className="flex flex-col gap-3 pt-1">
      {members ? (
        <AssignConversationForm
          action={action}
          members={members}
          currentId={currentId}
        />
      ) : (
        <Alert tone="danger">
          Team members could not be loaded. Refresh the page to try again.
        </Alert>
      )}
      {history ? (
        <details className="text-sm">
          <summary className="min-h-8 cursor-pointer py-1 text-secondary hover:text-primary">
            Assignment history ({history.totalElements})
          </summary>
          {history.content.length === 0 ? (
            <p className="py-2 text-secondary">
              Nobody has held this conversation yet.
            </p>
          ) : (
            <ul className="flex flex-col divide-y divide-line-subtle">
              {history.content.map((record) => (
                <li
                  key={record.id}
                  className="flex flex-wrap justify-between gap-x-4 py-2"
                >
                  <span>{historyLine(record)}</span>
                  <time
                    dateTime={record.createdAt}
                    className="figure text-caption text-secondary"
                  >
                    {formatDateTime(record.createdAt)}
                  </time>
                </li>
              ))}
            </ul>
          )}
        </details>
      ) : (
        <Alert tone="danger">Assignment history could not be loaded.</Alert>
      )}
    </section>
  );
}
