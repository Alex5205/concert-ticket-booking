package com.example.demo.scheduler;

import com.example.demo.entity.OutboxEvent;
import com.example.demo.repository.OutboxEventRepository;
import com.example.demo.service.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher eventPublisher;
    private final Clock clock;

    @Scheduled(fixedDelayString = "5000")
    @Transactional
    public void relayOutboxEvents() {
        List<OutboxEvent> batch = outboxEventRepository.findUnprocessedBatchForUpdate(BATCH_SIZE);
        for (OutboxEvent event : batch) {
            try {
                eventPublisher.publish(event);
                event.setProcessedAt(Instant.now(clock));
            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                log.error("Failed to publish outbox event {}, retryCount={}", event.getId(), event.getRetryCount(), ex);
            }
        }
    }
}
