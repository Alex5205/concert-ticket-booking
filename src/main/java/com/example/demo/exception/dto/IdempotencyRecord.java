package com.example.demo.dto;

public record IdempotencyRecord(
        Status status,
        int httpStatus,
        String responseBody
) {
    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }
}
