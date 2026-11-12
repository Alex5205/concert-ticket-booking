package com.example.demo.repository;

import com.example.demo.entity.Ticket;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdWithLock(@Param("id") Long id);

    @EntityGraph(attributePaths = {"flight", "user"})
    @Query("SELECT t FROM Ticket t")
    Page<Ticket> findAllWithGraph(Pageable pageable);

    @Modifying
    @Query(value = """
            UPDATE tickets
            SET status = 'AVAILABLE', user_id = NULL, reserved_until = NULL
            WHERE id IN (
                SELECT id FROM tickets
                WHERE status = 'RESERVED' AND reserved_until < :now
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            """, nativeQuery = true)
    int releaseExpiredReservations(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
