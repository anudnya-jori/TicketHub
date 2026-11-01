package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.domain.Show;
import com.tickethub.eventshow.domain.ShowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface ShowRepository extends JpaRepository<Show, Long>, JpaSpecificationExecutor<Show> {

    /** Used when an event is cancelled: its shows that are not finished are cancelled too. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Show s set s.status = :to, s.updatedAt = :now "
            + "where s.eventId = :eventId and s.status in :fromStatuses")
    int cancelByEventId(@Param("eventId") Long eventId,
                        @Param("fromStatuses") Collection<ShowStatus> fromStatuses,
                        @Param("to") ShowStatus to,
                        @Param("now") Instant now);
}
