package com.flowdesk.sla.repository;

import com.flowdesk.sla.domain.SlaPolicy;

import com.flowdesk.ticket.domain.TicketPriority;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SlaPolicyRepository
        extends JpaRepository<SlaPolicy, UUID> {

    Optional<SlaPolicy>
            findByPriorityAndActiveTrue(
                    TicketPriority priority
            );
}