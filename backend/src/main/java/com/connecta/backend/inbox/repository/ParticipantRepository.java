package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Participant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Optional<Participant> findByOrganizationIdAndChannelAndExternalId(UUID organizationId, Channel channel,
            String externalId);
}
