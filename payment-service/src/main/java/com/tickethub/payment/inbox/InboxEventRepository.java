package com.tickethub.payment.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface InboxEventRepository extends JpaRepository<InboxEvent, String> {

    /** Returns 1 if this call recorded the event, 0 if it was already there (duplicate). Race-free in PostgreSQL. */
    @Modifying
    @Query(value = "INSERT INTO inbox_events (event_id, event_type, booking_id, received_at) "
            + "VALUES (:eventId, :eventType, :bookingId, :receivedAt) ON CONFLICT (event_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("eventType") String eventType,
                       @Param("bookingId") Long bookingId, @Param("receivedAt") Instant receivedAt);
}
