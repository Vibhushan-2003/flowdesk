package com.flowdesk.ticket.controller;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.ticket.dto.ClaimTicketResponse;
import com.flowdesk.ticket.dto.CreateTicketCommentRequest;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketCommentResponse;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.dto.TicketSummaryResponse;

import com.flowdesk.ticket.service.TicketCommentService;
import com.flowdesk.ticket.service.TicketService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketCommentService ticketCommentService;

    public TicketController(
            TicketService ticketService,
            TicketCommentService ticketCommentService
    ) {
        this.ticketService = ticketService;
        this.ticketCommentService = ticketCommentService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTicketRequest request
    ) {
        UUID authenticatedUserId =
                UUID.fromString(jwt.getSubject());

        TicketResponse response =
                ticketService.createTicket(
                        authenticatedUserId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<PageResponse<TicketSummaryResponse>>
            getMyTickets(
                    @AuthenticationPrincipal Jwt jwt,
                    @RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "10") int size
            ) {

        validatePagination(page, size);

        UUID authenticatedUserId =
                UUID.fromString(jwt.getSubject());

        PageResponse<TicketSummaryResponse> response =
                ticketService.getMyTickets(
                        authenticatedUserId,
                        page,
                        size
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/queue")
    public ResponseEntity<PageResponse<TicketSummaryResponse>>
            getSupportQueue(
                    @RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "10") int size
            ) {

        validatePagination(page, size);

        PageResponse<TicketSummaryResponse> response =
                ticketService.getSupportQueue(
                        page,
                        size
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{ticketNumber}/claim")
    public ResponseEntity<ClaimTicketResponse> claimTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String ticketNumber
    ) {

        UUID authenticatedUserId =
                UUID.fromString(jwt.getSubject());

        ClaimTicketResponse response =
                ticketService.claimTicket(
                        authenticatedUserId,
                        ticketNumber
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
                UUID.fromString(jwt.getSubject());

        List<TicketCommentResponse> response =
                ticketCommentService.getEmployeeComments(
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
                UUID.fromString(jwt.getSubject());

        TicketCommentResponse response =
                ticketCommentService.addEmployeeComment(
                        authenticatedUserId,
                        ticketNumber,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{ticketNumber}")
    public ResponseEntity<TicketResponse> getMyTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String ticketNumber
    ) {

        UUID authenticatedUserId =
                UUID.fromString(jwt.getSubject());

        TicketResponse response =
                ticketService.getMyTicket(
                        authenticatedUserId,
                        ticketNumber
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