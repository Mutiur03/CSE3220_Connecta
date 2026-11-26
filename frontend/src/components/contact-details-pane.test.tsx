import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { ConversationDetail } from "@/lib/inbox-api";
import ContactDetailsPane from "./contact-details-pane";

const conversation: ConversationDetail = {
  id: "c1",
  organizationId: "o1",
  channel: "MESSENGER",
  status: "CLOSED",
  unread: false,
  participants: [
    {
      id: "p1",
      externalId: "psid-1",
      displayName: "Nadia Rahman",
      avatarUrl: null,
    },
  ],
  assignee: { membershipId: "m1", userId: "u1", name: "Mira Das" },
  lastMessageAt: "2026-06-01T10:00:00Z",
  createdAt: "2026-05-20T09:00:00Z",
  updatedAt: "2026-06-01T10:00:00Z",
};

describe("ContactDetailsPane", () => {
  it("shows the customer, channel, status and contact dates", () => {
    const markup = renderToStaticMarkup(
      <ContactDetailsPane conversation={conversation} />,
    );
    expect(markup).toContain('aria-label="Contact details"');
    expect(markup).toContain("Nadia Rahman");
    expect(markup).toContain(">Messenger<");
    expect(markup).toContain(">Closed<");
    expect(markup).toContain('dateTime="2026-05-20T09:00:00Z"');
    expect(markup).toContain("Last message");
    expect(markup).not.toContain("Also in this conversation");
  });

  it("shows the assignee, or Unassigned", () => {
    expect(
      renderToStaticMarkup(<ContactDetailsPane conversation={conversation} />),
    ).toContain("Mira Das");
    expect(
      renderToStaticMarkup(
        <ContactDetailsPane
          conversation={{ ...conversation, assignee: null }}
        />,
      ),
    ).toContain("Unassigned");
  });

  it("puts the assignment control in the Assignee row in place of the plain name", () => {
    const markup = renderToStaticMarkup(
      <ContactDetailsPane
        conversation={conversation}
        assignmentControl={<select id="assignee" />}
      />,
    );
    expect(markup).toContain('id="assignee"');
    expect(markup).not.toContain("Mira Das");
  });

  it("lists other customers and skips the last message when there is none", () => {
    const markup = renderToStaticMarkup(
      <ContactDetailsPane
        conversation={{
          ...conversation,
          lastMessageAt: null,
          participants: [
            ...conversation.participants,
            {
              id: "p2",
              externalId: "psid-2",
              displayName: null,
              avatarUrl: null,
            },
          ],
        }}
      />,
    );
    expect(markup).toContain("Also in this conversation");
    expect(markup).toContain("Unknown contact");
    expect(markup).not.toContain("Last message");
  });
});
