package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.AssignmentSource;
import com.connecta.backend.inbox.domain.ConversationAssignment;
import com.connecta.backend.org.domain.Membership;
import java.time.Instant;
import java.util.UUID;

/**
 * One hand-over in a conversation history. {@code from} is null for the first assignment, {@code to} for an
 * unassignment, and {@code assignedBy} for an automatic one.
 */
public record ConversationAssignmentResponse(UUID id, Person from, Person to, Person assignedBy,
        AssignmentSource source, Instant createdAt) {

    /** A person in the history. {@code membershipId} is null for the user who made the change. */
    public record Person(UUID membershipId, UUID userId, String name) {

        public static Person of(Membership member) {
            return member == null ? null
                    : new Person(member.getId(), member.getUser().getId(), member.getUser().getFullName());
        }
    }

    public static ConversationAssignmentResponse from(ConversationAssignment a) {
        Person by = a.getAssignedBy() == null ? null
                : new Person(null, a.getAssignedBy().getId(), a.getAssignedBy().getFullName());
        return new ConversationAssignmentResponse(a.getId(), Person.of(a.getFromMember()),
                Person.of(a.getToMember()), by, a.getSource(), a.getCreatedAt());
    }
}
