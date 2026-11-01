package com.tickethub.notification.repository;

import com.tickethub.notification.domain.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByEventId(String eventId);

    Optional<Notification> findByEventId(String eventId);

    List<Notification> findByBookingIdOrderByIdAsc(Long bookingId);

    /** SELECT ... FOR UPDATE: two consumer instances take turns on one notification, so it is sent once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> findByIdForUpdate(@Param("id") Long id);
}
