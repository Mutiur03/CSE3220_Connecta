package com.connecta.backend.org.repository;

import java.time.Instant;
import java.util.UUID;

/** One organization with its member count, read in a single query. */
public interface OrganizationSummary {

    UUID getId();

    String getName();

    String getSlug();

    long getMemberCount();

    Instant getCreatedAt();
}
