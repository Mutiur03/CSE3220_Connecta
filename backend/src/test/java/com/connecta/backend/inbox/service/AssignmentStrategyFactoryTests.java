package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentStrategyFactoryTests {

    @Test
    void anOrganizationGetsTheRoundRobinStrategyByDefault() {
        AssignmentStrategy roundRobin = mock(AssignmentStrategy.class);
        when(roundRobin.mode()).thenReturn(AssignmentMode.ROUND_ROBIN);

        assertSame(roundRobin, new AssignmentStrategyFactory(List.of(roundRobin)).forOrganization(UUID.randomUUID()));
    }

    @Test
    void aMissingStrategyThrowsADomainException() {
        AssignmentStrategyFactory factory = new AssignmentStrategyFactory(List.of());

        assertThrows(AssignmentStrategyNotFoundException.class, () -> factory.forOrganization(UUID.randomUUID()));
    }
}
