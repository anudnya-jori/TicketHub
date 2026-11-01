package com.tickethub.payment.repository;

import com.tickethub.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByProviderReference(String providerReference);

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    /** SELECT ... FOR UPDATE: two refund attempts for one payment take turns, so it is refunded once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.bookingId = :bookingId")
    Optional<Payment> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    java.util.List<Payment> findByStatus(com.tickethub.payment.domain.PaymentStatus status);

    java.util.List<Payment> findByStatusAndUpdatedAtBefore(com.tickethub.payment.domain.PaymentStatus status, java.time.Instant before);
}
