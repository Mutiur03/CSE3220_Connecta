package com.connecta.backend.inbox.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.user.domain.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConversationTests {

    private final Organization org = new Organization("Acme", "acme");

    @Nested
    class AssignTo {

        @Test
        void recordsTheFirstAssignmentFromNobody() {
            Conversation conversation = new Conversation(org, Channel.INSTAGRAM, "t-1");
            Membership member = mock(Membership.class);

            ConversationAssignment record = conversation.assignTo(member, null, AssignmentSource.AUTO_ROUND_ROBIN);

            assertSame(member, conversation.getAssignee());
            assertNull(record.getFromMember());
            assertSame(member, record.getToMember());
            assertSame(org, record.getOrganization());
            assertEquals(AssignmentSource.AUTO_ROUND_ROBIN, record.getSource());
        }

        @Test
        void recordsTheHandOverFromTheCurrentAssignee() {
            Conversation conversation = new Conversation(org, Channel.MESSENGER, "t-2");
            Membership first = mock(Membership.class);
            Membership second = mock(Membership.class);
            User admin = mock(User.class);
            conversation.assignTo(first, null, AssignmentSource.AUTO_ROUND_ROBIN);

            ConversationAssignment record = conversation.assignTo(second, admin, AssignmentSource.MANUAL);

            assertSame(first, record.getFromMember());
            assertSame(second, record.getToMember());
            assertSame(admin, record.getAssignedBy());
            assertSame(second, conversation.getAssignee());
        }

        @Test
        void unassignsWithNull() {
            Conversation conversation = new Conversation(org, Channel.MESSENGER, "t-3");
            Membership member = mock(Membership.class);
            conversation.assignTo(member, null, AssignmentSource.AUTO_ROUND_ROBIN);

            ConversationAssignment record = conversation.assignTo(null, null, AssignmentSource.MANUAL);

            assertNull(conversation.getAssignee());
            assertSame(member, record.getFromMember());
            assertNull(record.getToMember());
        }
    }
}
