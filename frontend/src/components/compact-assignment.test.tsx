import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import AssignConversationForm from "./assign-conversation-form";
import CompactAssignment from "./compact-assignment";

const action = async () => ({});

describe("CompactAssignment", () => {
  it("is hidden from xl up and uses its own ids", () => {
    const html = renderToStaticMarkup(
      <CompactAssignment action={action} members={[]} currentId={null} />,
    );
    expect(html).toContain("xl:hidden");
    expect(html).toContain('id="assignee-compact"');
    expect(html).toContain('for="assignee-compact"');
    expect(html).toContain('id="assignee-compact-status"');
    expect(html).not.toContain('id="assignee"');
  });

  it("never shares an id with the pane form on the same page", () => {
    const html = renderToStaticMarkup(
      <>
        <CompactAssignment action={action} members={[]} currentId={null} />
        <AssignConversationForm action={action} members={[]} currentId={null} />
      </>,
    );
    const ids = [...html.matchAll(/ id="([^"]+)"/g)].map((m) => m[1]);
    expect(new Set(ids).size).toBe(ids.length);
    expect(ids).toContain("assignee");
  });
});
