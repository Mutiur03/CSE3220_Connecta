package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Participant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    Optional<Participant> findByOrganizationIdAndChannelAndExternalId(UUID organizationId, Channel channel,
            String externalId);

    /** Sets the name only while it is empty, so a name already known is never overwritten. Returns rows changed. */
    @Modifying
    @Query("update Participant p set p.displayName = :name where p.organization.id = :organizationId"
            + " and p.channel = :channel and p.externalId = :externalId and p.displayName is null")
    int nameIfAbsent(@Param("organizationId") UUID organizationId, @Param("channel") Channel channel,
            @Param("externalId") String externalId, @Param("name") String name);
}
