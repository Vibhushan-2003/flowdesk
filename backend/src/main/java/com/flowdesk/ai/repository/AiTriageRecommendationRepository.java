package com.flowdesk.ai.repository;

import com.flowdesk.ai.domain.AiTriageRecommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiTriageRecommendationRepository
        extends JpaRepository<
                AiTriageRecommendation,
                UUID
        > {

    Optional<AiTriageRecommendation>
            findByTicket_Id(
                    UUID ticketId
            );

    boolean existsByTicket_Id(
            UUID ticketId
    );
}