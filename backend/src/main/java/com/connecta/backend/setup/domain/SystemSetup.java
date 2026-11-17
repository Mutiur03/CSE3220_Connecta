package com.connecta.backend.setup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The one row that records the first-run setup: who became the system owner, and when. */
@Entity
@Table(name = "system_setup")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemSetup {

    @Id
    private Short id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}
