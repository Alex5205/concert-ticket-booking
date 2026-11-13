package com.example.demo.dto;

import com.example.demo.entity.TicketStatus;

public record BookingResponse(
        Long ticketId,
        String seatNumber,
        TicketStatus status,
        String message
) {
}
