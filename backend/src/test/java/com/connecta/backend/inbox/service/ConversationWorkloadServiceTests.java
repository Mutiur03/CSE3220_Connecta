package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.repository.ConversationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationWorkloadServiceTests {

    @Mock
    ConversationRepository conversations;

    @InjectMocks
    ConversationWorkloadService service;

    private final UUID organizationId = UUID.randomUUID();

    @Test
    void membersWithoutRowsCountZeroAndOthersTheirGroupedCount() {
        UUID busy = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        when(conversations.countOpenByAssignee(organizationId, List.of(busy, idle)))
                .thenReturn(List.<Object[]>of(new Object[] {busy, 3L}));

        Map<UUID, Long> counts = service.openCounts(organizationId, List.of(busy, idle));

        assertEquals(3L, counts.get(busy));
        assertEquals(0L, counts.get(idle));
    }

    @Test
    void noMembersMeansNoQuery() {
        assertEquals(Map.of(), service.openCounts(organizationId, List.of()));
        verifyNoInteractions(conversations);
    }
}
