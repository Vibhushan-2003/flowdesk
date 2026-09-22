package com.flowdesk.ticket.controller;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.ticket.dto.CreateTicketCommentRequest;
import com.flowdesk.ticket.dto.SupportTicketResponse;
import com.flowdesk.ticket.dto.SupportTicketSummaryResponse;
import com.flowdesk.ticket.dto.TicketCommentResponse;
import com.flowdesk.ticket.dto.UpdateTicketStatusRequest;

import com.flowdesk.ticket.service.SupportTicketService;
import com.flowdesk.ticket.service.TicketCommentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/support/tickets")
public class SupportTicketController {

    private final SupportTicketService
            supportTicketService;

    private final TicketCommentService
            ticketCommentService;

    public SupportTicketController(
            SupportTicketService supportTicketService,
            TicketCommentService ticketCommentService
    ) {
        this.supportTicketService =
                supportTicketService;

        this.ticketCommentService =
                ticketCommentService;
    }

    @GetMapping("/my")
    public ResponseEntity<
            PageResponse<SupportTicketSummaryResponse>>
            getMyAssignedTickets(
                    @AuthenticationPrincipal Jwt jwt,
                    @RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "10") int size
            ) {

        validatePagination(page, size);

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        PageResponse<SupportTicketSummaryResponse> response =
                supportTicketService
                        .getMyAssignedTickets(
                                authenticatedUserId,
                                page,
                                size
                        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{ticketNumber}/comments")
    public ResponseEntity<List<TicketCommentResponse>>
            getTicketComments(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        List<TicketCommentResponse> response =
                ticketCommentService
                        .getSupportComments(
                                authenticatedUserId,
                                ticketNumber
                        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{ticketNumber}/comments")
    public ResponseEntity<TicketCommentResponse>
            addTicketComment(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber,
                    @Valid
                    @RequestBody
                    CreateTicketCommentRequest request
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        TicketCommentResponse response =
                ticketCommentService
                        .addSupportComment(
                                authenticatedUserId,
                                ticketNumber,
                                request
                        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{ticketNumber}")
    public ResponseEntity<SupportTicketResponse>
            getMyAssignedTicket(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        SupportTicketResponse response =
                supportTicketService
                        .getMyAssignedTicket(
                                authenticatedUserId,
                                ticketNumber
                        );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{ticketNumber}/status")
    public ResponseEntity<SupportTicketResponse>
            updateStatus(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber,
                    @Valid
                    @RequestBody
                    UpdateTicketStatusRequest request
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        SupportTicketResponse response =
                supportTicketService
                        .updateStatus(
                                authenticatedUserId,
                                ticketNumber,
                                request
                        );

        return ResponseEntity.ok(response);
    }

    private void validatePagination(
            int page,
            int size
    ) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }
    }
}