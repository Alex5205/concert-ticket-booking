package com.example.demo.service;

import com.example.demo.entity.Ticket;
import com.example.demo.entity.TicketStatus;
import com.example.demo.entity.User;
import com.example.demo.exception.*;
import com.example.demo.repository.TicketRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public Ticket reserveTicket(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));

        Instant now = Instant.now(clock);

        // Ленивая очистка: если бронь просрочена, освобождаем билет прямо сейчас
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

        return ticketRepository.save(ticket);
    }
}
