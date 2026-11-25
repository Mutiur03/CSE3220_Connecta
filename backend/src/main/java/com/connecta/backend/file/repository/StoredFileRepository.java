package com.connecta.backend.file.repository;

import com.connecta.backend.file.domain.StoredFile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    /** Deletes the file without loading its bytes. Scoped by organization; returns the number of rows removed. */
    @Modifying
    @Query("delete from StoredFile f where f.id = :id and f.organizationId = :organizationId")
    int deleteByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
