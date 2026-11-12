package com.example.demo.scheduler;

import com.example.demo.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCleanupScheduler {

    private static final int BATCH_SIZE = 500;

    private final TicketRepository ticketRepository;
    private final Clock clock;

    @Scheduled(fixedDelayString = "10000")
    @Transactional
    public void cleanupExpiredReservations() {
        int released = ticketRepository.releaseExpiredReservations(Instant.now(clock), BATCH_SIZE);
        if (released > 0) {
            log.info("Released {} expired ticket reservations", released);
        }
    }
}
