package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.repository.ConversationRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** How many open conversations each member holds. Other features ask here instead of the repository. */
@Service
@RequiredArgsConstructor
public class ConversationWorkloadService {

    private final ConversationRepository conversations;

    /** Open conversations per member of the organization, one grouped query. Members with none map to 0. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> openCounts(UUID organizationId, Collection<UUID> membershipIds) {
        Map<UUID, Long> counts = new HashMap<>();
        membershipIds.forEach(id -> counts.put(id, 0L));
        if (!membershipIds.isEmpty()) {
            conversations.countOpenByAssignee(organizationId, membershipIds)
                    .forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
        }
        return counts;
    }
}
