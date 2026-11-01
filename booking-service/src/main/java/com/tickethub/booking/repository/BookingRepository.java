package com.tickethub.booking.repository;

import com.tickethub.booking.domain.Booking;
import com.tickethub.booking.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);

    /**
     * SELECT ... FOR UPDATE on the booking row. Payment result, cancel and expiry all change a booking's
     * status; locking the row makes them take turns instead of overwriting each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("select b.id from Booking b where b.status = :status and b.expiryAt < :now")
    List<Long> findIdsByStatusAndExpiryAtBefore(@Param("status") BookingStatus status, @Param("now") Instant now);
}
