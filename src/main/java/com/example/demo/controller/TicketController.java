package com.example.demo.controller;

import com.example.demo.dto.BookingResponse;
import com.example.demo.dto.IdempotencyRecord;
import com.example.demo.entity.Ticket;
import com.example.demo.entity.TicketStatus;
import com.example.demo.security.AuthenticatedUser;
import com.example.demo.service.IdempotencyService;
import com.example.demo.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/{ticketId}/reserve")
    public ResponseEntity<BookingResponse> reserveTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        ticketService.reserveTicket(ticketId, currentUser.userId());

        BookingResponse response = new BookingResponse(
                ticketId,
                null,
                TicketStatus.RESERVED,
                "Билет успешно зарезервирован на 15 минут"
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{ticketId}/pay")
    public ResponseEntity<?> payTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        String scopedKey = currentUser.userId() + ":" + idempotencyKey;
        Optional<IdempotencyRecord> cachedRecord = idempotencyService.acquireOrGet(scopedKey);

        if (cachedRecord.isPresent()) {
            IdempotencyRecord record = cachedRecord.get();
            return ResponseEntity
                    .status(record.httpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(record.responseBody());
        }

        try {
            Ticket ticket = ticketService.confirmBooking(ticketId, currentUser.userId());

            BookingResponse response = new BookingResponse(
                    ticket.getId(),
                    ticket.getSeatNumber(),
                    ticket.getStatus(),
                    "Оплата прошла успешно, билет куплен"
            );

            idempotencyService.saveCompleted(scopedKey, HttpStatus.OK.value(), response);

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            idempotencyService.releaseOnError(scopedKey);
            throw ex;
        }
    }
}
