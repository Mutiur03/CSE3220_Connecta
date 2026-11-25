package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.ConversationStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Specification pattern: each conversation list filter is a small predicate, combined with AND. */
public final class ConversationSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ConversationSpecifications() {
    }

    public static Specification<Conversation> inOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    public static Specification<Conversation> onChannel(Channel channel) {
        return (root, query, cb) -> cb.equal(root.get("channel"), channel);
    }

    public static Specification<Conversation> withStatus(ConversationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Some customer of the conversation has a display name containing the text, ignoring case. */
    public static Specification<Conversation> participantNameContains(String text) {
        String pattern = "%" + escapeLike(text.toLowerCase()) + "%";
        return (root, query, cb) -> {
            var sub = query.subquery(UUID.class);
            var link = sub.from(ConversationParticipant.class);
            sub.select(link.get("id")).where(
                    cb.equal(link.get("conversation"), root),
                    cb.like(cb.lower(link.get("participant").get("displayName")), pattern, LIKE_ESCAPE));
            return cb.exists(sub);
        };
    }

    private static String escapeLike(String text) {
        String escape = String.valueOf(LIKE_ESCAPE);
        return text.replace(escape, escape + escape)
                .replace("%", escape + "%")
                .replace("_", escape + "_");
    }
}
