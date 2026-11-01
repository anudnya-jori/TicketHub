package com.tickethub.notification.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface InboxEventRepository extends JpaRepository<InboxEvent, String> {

    /**
     * Race-free "first one wins": INSERT ... ON CONFLICT DO NOTHING. Returns 1 if this call recorded the event,
     * 0 if the event id was already there (a duplicate). If another transaction is inserting the same id right now,
     * PostgreSQL makes this statement wait until that transaction ends, so a "check, then insert" race cannot happen.
     */
    @Modifying
    @Query(value = "INSERT INTO inbox_events (event_id, event_type, booking_id, received_at) "
            + "VALUES (:eventId, :eventType, :bookingId, :receivedAt) ON CONFLICT (event_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("eventType") String eventType,
                       @Param("bookingId") Long bookingId, @Param("receivedAt") Instant receivedAt);
}
