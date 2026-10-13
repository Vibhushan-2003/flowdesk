package com.flowdesk.ai.domain;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketPriority;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mock;

class AiTriageRecommendationTest {

    @Test
    void constructorNormalizesValues() {

        Ticket ticket =
                mock(Ticket.class);

        User engineer =
                mock(User.class);

        OffsetDateTime createdAt =
                OffsetDateTime.parse(
                        "2026-10-11T20:00:00+05:30"
                );

        AiTriageRecommendation recommendation =
                new AiTriageRecommendation(
                        ticket,
                        engineer,
                        TicketPriority.HIGH,
                        new BigDecimal(
                                "0.87654"
                        ),
                        "  Major work is blocked.  ",
                        "  GOOGLE_GEMINI  ",
                        "  gemini-3.8-flash  ",
                        "  triage-v1  ",
                        createdAt
                );

        assertEquals(
                TicketPriority.HIGH,
                recommendation
                        .getSuggestedPriority()
        );

        assertEquals(
                new BigDecimal(
                        "0.8765"
                ),
                recommendation
                        .getConfidence()
        );

        assertEquals(
                "Major work is blocked.",
                recommendation.getReason()
        );

        assertEquals(
                "GOOGLE_GEMINI",
                recommendation.getProvider()
        );

        assertEquals(
                "gemini-3.8-flash",
                recommendation.getModel()
        );

        assertEquals(
                "triage-v1",
                recommendation
                        .getPromptVersion()
        );

        assertEquals(
                createdAt.withOffsetSameInstant(
                        ZoneOffset.UTC
                ),
                recommendation.getCreatedAt()
        );
    }

    @Test
    void confidenceBelowZeroIsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiTriageRecommendation(
                                mock(Ticket.class),
                                mock(User.class),
                                TicketPriority.LOW,
                                new BigDecimal(
                                        "-0.01"
                                ),
                                "Reason",
                                "GOOGLE_GEMINI",
                                "gemini-3.8-flash",
                                "triage-v1",
                                OffsetDateTime.now(
                                        ZoneOffset.UTC
                                )
                        )
        );
    }

    @Test
    void confidenceAboveOneIsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiTriageRecommendation(
                                mock(Ticket.class),
                                mock(User.class),
                                TicketPriority.CRITICAL,
                                new BigDecimal(
                                        "1.01"
                                ),
                                "Reason",
                                "GOOGLE_GEMINI",
                                "gemini-3.8-flash",
                                "triage-v1",
                                OffsetDateTime.now(
                                        ZoneOffset.UTC
                                )
                        )
        );
    }

    @Test
    void blankReasonIsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiTriageRecommendation(
                                mock(Ticket.class),
                                mock(User.class),
                                TicketPriority.MEDIUM,
                                new BigDecimal(
                                        "0.75"
                                ),
                                "   ",
                                "GOOGLE_GEMINI",
                                "gemini-3.8-flash",
                                "triage-v1",
                                OffsetDateTime.now(
                                        ZoneOffset.UTC
                                )
                        )
        );
    }
}