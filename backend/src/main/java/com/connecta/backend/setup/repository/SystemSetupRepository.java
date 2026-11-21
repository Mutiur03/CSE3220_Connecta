package com.connecta.backend.setup.repository;

import com.connecta.backend.setup.domain.SystemSetup;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemSetupRepository extends JpaRepository<SystemSetup, Short> {

    /**
     * Writes the single setup row. Returns 1 for the winner and 0 when a row already exists, so two
     * setups racing each other cannot both complete.
     */
    @Modifying
    @Query(value = "INSERT INTO system_setup (id, owner_id, completed_at) VALUES (1, :ownerId, :now) "
            + "ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    int claim(@Param("ownerId") UUID ownerId, @Param("now") Instant now);
}
