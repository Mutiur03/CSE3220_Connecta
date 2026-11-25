package com.connecta.backend.meta.repository;

import com.connecta.backend.meta.domain.MetaViewOnceMark;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetaViewOnceMarkRepository extends JpaRepository<MetaViewOnceMark, Long> {

    /** Records the timestamp; a repeat (Meta retry, second delivery) is a no-op, never a constraint error. */
    @Modifying
    @Query(value = "insert into meta_view_once_marks (sent_at_ms, created_at) values (:sentAtMs, :now)"
            + " on conflict (sent_at_ms) do nothing", nativeQuery = true)
    void record(@Param("sentAtMs") long sentAtMs, @Param("now") Instant now);

    /** Deletes marks created before the cutoff; returns how many. */
    @Modifying
    @Query("delete from MetaViewOnceMark m where m.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
