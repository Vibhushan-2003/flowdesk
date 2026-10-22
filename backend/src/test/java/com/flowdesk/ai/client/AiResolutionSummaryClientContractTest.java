package com.flowdesk.ai.client;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiResolutionSummaryClientContractTest {

    @Test
    void requestNormalizesTextAndCopiesConversation() {

        List<AiResolutionSummaryConversationMessage>
                conversation =
                List.of(
                        new AiResolutionSummaryConversationMessage(
                                AiResolutionSummaryMessageRole.REQUESTER,
                                "Payroll page does not load."
                        ),

                        new AiResolutionSummaryConversationMessage(
                                AiResolutionSummaryMessageRole.SUPPORT,
                                "Access permissions were corrected."
                        )
                );

        AiResolutionSummaryClientRequest request =
                new AiResolutionSummaryClientRequest(
                        TicketType.INCIDENT,
                        TicketPriority.HIGH,
                        TicketStatus.IN_PROGRESS,
                        "  Payroll access issue  ",
                        "  User cannot open payroll.  ",
                        conversation
                );

        assertEquals(
                "Payroll access issue",
                request.title()
        );

        assertEquals(
                "User cannot open payroll.",
                request.description()
        );

        assertEquals(
                2,
                request.conversation().size()
        );
    }

    @Test
    void requestRejectsBlankTitle() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiResolutionSummaryClientRequest(
                                TicketType.INCIDENT,
                                TicketPriority.MEDIUM,
                                TicketStatus.IN_PROGRESS,
                                "   ",
                                "Description",
                                List.of()
                        )
        );
    }

    @Test
    void requestRejectsBlankDescription() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiResolutionSummaryClientRequest(
                                TicketType.INCIDENT,
                                TicketPriority.MEDIUM,
                                TicketStatus.IN_PROGRESS,
                                "Title",
                                "   ",
                                List.of()
                        )
        );
    }

    @Test
    void requestRejectsNullConversationEntry() {

        List<AiResolutionSummaryConversationMessage>
                conversation =
                new java.util.ArrayList<>();

        conversation.add(
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiResolutionSummaryClientRequest(
                                TicketType.INCIDENT,
                                TicketPriority.MEDIUM,
                                TicketStatus.IN_PROGRESS,
                                "Title",
                                "Description",
                                conversation
                        )
        );
    }

    @Test
    void conversationMessageNormalizesBody() {

        AiResolutionSummaryConversationMessage message =
                new AiResolutionSummaryConversationMessage(
                        AiResolutionSummaryMessageRole.SUPPORT,
                        "  Restarted the affected service.  "
                );

        assertEquals(
                "Restarted the affected service.",
                message.body()
        );
    }

    @Test
    void conversationMessageRejectsBlankBody() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiResolutionSummaryConversationMessage(
                                AiResolutionSummaryMessageRole.SUPPORT,
                                "   "
                        )
        );
    }

    @Test
    void resultNormalizesGeneratedValues() {

        AiResolutionSummaryClientResult result =
                new AiResolutionSummaryClientResult(
                        "  Access permissions were corrected and login was confirmed.  ",
                        "  GOOGLE_GEMINI  ",
                        "  gemini-3.1-flash-lite  "
                );

        assertEquals(
                "Access permissions were corrected and login was confirmed.",
                result.draft()
        );

        assertEquals(
                "GOOGLE_GEMINI",
                result.provider()
        );

        assertEquals(
                "gemini-3.1-flash-lite",
                result.model()
        );
    }

    @Test
    void resultRejectsBlankDraft() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiResolutionSummaryClientResult(
                                "   ",
                                "GOOGLE_GEMINI",
                                "gemini-3.1-flash-lite"
                        )
        );
    }

    @Test
    void resultRejectsOversizedDraft() {

        String oversizedDraft =
                "a".repeat(
                        2001
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiResolutionSummaryClientResult(
                                oversizedDraft,
                                "GOOGLE_GEMINI",
                                "gemini-3.1-flash-lite"
                        )
        );
    }

    @Test
    void resultRejectsBlankProvider() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiResolutionSummaryClientResult(
                                "Resolved summary",
                                "   ",
                                "gemini-3.1-flash-lite"
                        )
        );
    }

    @Test
    void resultRejectsBlankModel() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AiResolutionSummaryClientResult(
                                "Resolved summary",
                                "GOOGLE_GEMINI",
                                "   "
                        )
        );
    }
}