package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ParticipantNameServiceTests {

    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final ParticipantNameService service = new ParticipantNameService(participants);
    private final UUID organizationId = UUID.randomUUID();

    @Test
    void setsTheTrimmedNameOnlyWhereNoneExists() {
        service.nameIfAbsent(organizationId, Channel.MESSENGER, "c1", "  Rina Akter ");

        verify(participants).nameIfAbsent(organizationId, Channel.MESSENGER, "c1", "Rina Akter");
    }

    @Test
    void aBlankOrMissingNameChangesNothing() {
        service.nameIfAbsent(organizationId, Channel.MESSENGER, "c1", "  ");
        service.nameIfAbsent(organizationId, Channel.MESSENGER, "c1", null);

        verify(participants, never()).nameIfAbsent(any(), any(), anyString(), anyString());
    }

    @Test
    void aLongNameIsCutToTheColumnLimit() {
        service.nameIfAbsent(organizationId, Channel.INSTAGRAM, "c1", "x".repeat(400));

        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(participants).nameIfAbsent(any(), any(), anyString(), name.capture());
        assertEquals(150, name.getValue().length());
    }
}
