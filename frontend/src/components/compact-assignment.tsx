import AssignConversationForm from "./assign-conversation-form";

type Props = React.ComponentProps<typeof AssignConversationForm>;

/**
 * Admin only: the assignee select in the thread header below 1280px, where the details pane is hidden.
 * From xl up the pane holds the same form, so this one is hidden; the ids differ so the page never has two
 * elements with one id.
 */
export default function CompactAssignment(props: Omit<Props, "idPrefix">) {
  return (
    <div className="basis-full sm:basis-56 xl:hidden">
      <AssignConversationForm {...props} idPrefix="assignee-compact" />
    </div>
  );
}
