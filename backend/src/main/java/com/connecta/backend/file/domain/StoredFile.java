package com.connecta.backend.file.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The bytes of one file an organization stored, served publicly by its unguessable id. The organization is kept
 * as its id (the foreign key and its cascade live in the migration), so this feature needs no other feature's
 * entities. Immutable once stored.
 */
@Entity
@Table(name = "stored_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile {

    public static final int CONTENT_TYPE_MAX_LENGTH = 100;
    public static final int FILE_NAME_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "content_type", nullable = false, length = CONTENT_TYPE_MAX_LENGTH, updatable = false)
    private String contentType;

    @Column(name = "file_name", length = FILE_NAME_MAX_LENGTH, updatable = false)
    private String fileName;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StoredFile(UUID organizationId, byte[] data, String contentType, String fileName) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.data = Objects.requireNonNull(data, "data");
        this.sizeBytes = data.length;
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.fileName = fileName;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof StoredFile other && getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return StoredFile.class.hashCode();
    }
}
