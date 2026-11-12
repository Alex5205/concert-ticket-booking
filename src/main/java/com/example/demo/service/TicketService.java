package com.example.demo.service;

import com.example.demo.entity.OutboxEvent;
import com.example.demo.entity.Ticket;
import com.example.demo.entity.TicketStatus;
import com.example.demo.entity.User;
import com.example.demo.exception.*;
import com.example.demo.repository.OutboxEventRepository;
import com.example.demo.repository.TicketRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public Ticket reserveTicket(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));

        Instant now = Instant.now(clock);

        if (ticket.getStatus() == TicketStatus.RESERVED && ticket.getReservedUntil().isBefore(now)) {
            ticket.setStatus(TicketStatus.AVAILABLE);
            ticket.setUser(null);
            ticket.setReservedUntil(null);
        }

        if (ticket.getStatus() != TicketStatus.AVAILABLE) {
            throw new TicketAlreadyBookedException("Ticket is already reserved or sold");
        }

        User user = userRepository.getReferenceById(userId);
        ticket.setStatus(TicketStatus.RESERVED);
        ticket.setUser(user);
        ticket.setReservedUntil(now.plus(RESERVATION_TTL));

        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket confirmBooking(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));

        if (ticket.getStatus() != TicketStatus.RESERVED) {
            throw new InvalidBookingStateException("Ticket is not in RESERVED state");
        }

        if (!ticket.getUser().getId().equals(userId)) {
            throw new BookingAccessDeniedException("Ticket is reserved by another user");
        }

        Instant now = Instant.now(clock);
        if (ticket.getReservedUntil().isBefore(now)) {
            throw new ReservationExpiredException("Reservation has expired");
        }

        ticket.setStatus(TicketStatus.SOLD);
        ticket.setReservedUntil(null);
        Ticket savedTicket = ticketRepository.save(ticket);

        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("TICKET")
                .aggregateId(savedTicket.getId().toString())
                .eventType("CONCERT_TICKET_PURCHASED")
                .payload(serializePayload(savedTicket, userId, now))
                .createdAt(now)
                .build();
        outboxEventRepository.save(event);

        return savedTicket;
    }

    private String serializePayload(Ticket ticket, Long userId, Instant purchasedAt) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "ticketId", ticket.getId(),
                    "seatNumber", ticket.getSeatNumber(),
                    "userId", userId,
                    "purchasedAt", purchasedAt.toString()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
