package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.ConversationParticipant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {
}
