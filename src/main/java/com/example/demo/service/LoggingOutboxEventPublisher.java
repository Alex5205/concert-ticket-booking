package com.example.demo.service;

import com.example.demo.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingOutboxEventPublisher implements OutboxEventPublisher {

    @Override
    public void publish(OutboxEvent event) {
        log.info("Publishing outbox event [id={}, type={}, payload={}]",
                event.getId(), event.getEventType(), event.getPayload());
    }
}
