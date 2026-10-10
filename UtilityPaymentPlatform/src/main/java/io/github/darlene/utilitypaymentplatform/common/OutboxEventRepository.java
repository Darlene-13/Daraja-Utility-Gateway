package io.github.darlene.utilitypaymentplatform.common;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository <OutboxEvent, UUID> {

    // SKIP LOCKED lets several app instances poll at once without grabbing the same rows.
    // The row locks hold until the caller's transaction ends, so call this inside one.
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(int limit);
}
