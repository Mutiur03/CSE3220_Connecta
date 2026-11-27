package com.connecta.backend.inbox.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Factory: picks the strategy for an organization. Every organization uses {@link AssignmentMode#DEFAULT}
 * until a per-organization setting exists; a missing strategy throws rather than silently skipping.
 */
@Component
public class AssignmentStrategyFactory {

    private final Map<AssignmentMode, AssignmentStrategy> byMode = new EnumMap<>(AssignmentMode.class);

    public AssignmentStrategyFactory(List<AssignmentStrategy> strategies) {
        strategies.forEach(strategy -> byMode.put(strategy.mode(), strategy));
    }

    public AssignmentStrategy forOrganization(UUID organizationId) {
        return forMode(AssignmentMode.DEFAULT);
    }

    public AssignmentStrategy forMode(AssignmentMode mode) {
        AssignmentStrategy strategy = byMode.get(mode);
        if (strategy == null) {
            throw new AssignmentStrategyNotFoundException(mode);
        }
        return strategy;
    }
}
