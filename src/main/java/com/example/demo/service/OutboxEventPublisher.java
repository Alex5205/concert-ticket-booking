package com.example.demo.service;

import com.example.demo.entity.OutboxEvent;

public interface OutboxEventPublisher {
    void publish(OutboxEvent event);
}
