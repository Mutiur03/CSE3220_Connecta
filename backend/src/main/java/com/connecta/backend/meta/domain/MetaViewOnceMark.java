package com.connecta.backend.meta.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The millisecond timestamp of a view-once photo's sender echo. The receiver's event carries the same timestamp,
 * so this is how a received photo is recognised as view-once. Holds no tenant data and no message content.
 */
@Entity
@Table(name = "meta_view_once_marks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaViewOnceMark {

    @Id
    @Column(name = "sent_at_ms")
    private long sentAtMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
