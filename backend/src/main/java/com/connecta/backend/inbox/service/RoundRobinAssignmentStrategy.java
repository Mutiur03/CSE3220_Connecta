package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.org.domain.AgentAvailability;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.repository.AgentAvailabilityRepository;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Round-robin: the available, enabled member who has waited longest (never assigned first) takes the
 * conversation. The repository locks the chosen row, so two chats arriving together pick different agents.
 */
@Component
@RequiredArgsConstructor
public class RoundRobinAssignmentStrategy implements AssignmentStrategy {

    private final AgentAvailabilityRepository availability;
    private final Clock clock;

    @Override
    public AssignmentMode mode() {
        return AssignmentMode.ROUND_ROBIN;
    }

    @Override
    public Optional<Membership> choose(Conversation conversation) {
        return availability.lockNextAvailable(conversation.getOrganization().getId()).stream().findFirst()
                .map(this::take);
    }

    private Membership take(AgentAvailability row) {
        row.recordAssignmentAt(clock.instant());
        return row.getMembership();
    }
}
