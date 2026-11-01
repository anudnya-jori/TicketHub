package com.tickethub.booking.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByIdAsc();

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByAggregateIdOrderByIdAsc(Long aggregateId);

    @Modifying
    @Query("update OutboxEvent e set e.publishedAt = :now where e.id = :id and e.publishedAt is null")
    int markPublished(@Param("id") Long id, @Param("now") Instant now);
}
