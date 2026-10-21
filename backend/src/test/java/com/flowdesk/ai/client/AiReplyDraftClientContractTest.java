package com.flowdesk.ai.client;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiReplyDraftClientContractTest {

    @Test
    void requestShouldNormalizeTicketText() {

        AiReplyDraftClientRequest request =
                new AiReplyDraftClientRequest(
                        TicketType.INCIDENT,
                        TicketPriority.HIGH,
                        TicketStatus.IN_PROGRESS,
                        "  VPN unavailable  ",
                        "  Users cannot connect to VPN.  ",
                        List.of()
                );

        assertEquals(
                "VPN unavailable",
                request.title()
        );

        assertEquals(
                "Users cannot connect to VPN.",
                request.description()
        );
    }

    @Test
    void conversationMessageShouldNormalizeBody() {

        AiReplyDraftConversationMessage message =
                new AiReplyDraftConversationMessage(
                        AiReplyDraftMessageRole.REQUESTER,
                        "  I still cannot connect.  "
                );

        assertEquals(
                AiReplyDraftMessageRole.REQUESTER,
                message.role()
        );

        assertEquals(
                "I still cannot connect.",
                message.body()
        );
    }

    @Test
    void requestShouldAllowEmptyConversation() {

        AiReplyDraftClientRequest request =
                createRequest(
                        List.of()
                );

        assertEquals(
                0,
                request
                        .conversation()
                        .size()
        );
    }

    @Test
    void requestShouldDefensivelyCopyConversation() {

        List<AiReplyDraftConversationMessage>
                conversation =
                new ArrayList<>();

        conversation.add(
                new AiReplyDraftConversationMessage(
                        AiReplyDraftMessageRole.REQUESTER,
                        "The issue is still happening."
                )
        );

        AiReplyDraftClientRequest request =
                createRequest(
                        conversation
                );

        conversation.clear();

        assertEquals(
                1,
                request
                        .conversation()
                        .size()
        );
    }

    @Test
    void requestShouldRejectNullConversationMessage() {

        List<AiReplyDraftConversationMessage>
                conversation =
                new ArrayList<>();

        conversation.add(
                null
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                createRequest(
                                        conversation
                                )
                );

        assertEquals(
                "Conversation must not contain null messages",
                exception.getMessage()
        );
    }

    @Test
    void conversationMessageShouldRejectBlankBody() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new AiReplyDraftConversationMessage(
                                        AiReplyDraftMessageRole.SUPPORT,
                                        "   "
                                )
                );

        assertEquals(
                "Conversation message is required",
                exception.getMessage()
        );
    }

    @Test
    void conversationMessageShouldRejectOversizedBody() {

        String oversizedBody =
                "a".repeat(
                        4001
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new AiReplyDraftConversationMessage(
                                        AiReplyDraftMessageRole.REQUESTER,
                                        oversizedBody
                                )
                );

        assertEquals(
                "Conversation message must not exceed 4000 characters",
                exception.getMessage()
        );
    }

    @Test
    void resultShouldNormalizeValues() {

        AiReplyDraftClientResult result =
                new AiReplyDraftClientResult(
                        "  Hi, we are investigating your issue.  ",
                        "  GOOGLE_GEMINI  ",
                        "  gemini-3.1-flash-lite  "
                );

        assertEquals(
                "Hi, we are investigating your issue.",
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
    void resultShouldRejectBlankDraft() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new AiReplyDraftClientResult(
                                        "   ",
                                        "GOOGLE_GEMINI",
                                        "gemini-3.1-flash-lite"
                                )
                );

        assertEquals(
                "AI reply draft is required",
                exception.getMessage()
        );
    }

    @Test
    void resultShouldRejectOversizedDraft() {

        String oversizedDraft =
                "a".repeat(
                        2001
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new AiReplyDraftClientResult(
                                        oversizedDraft,
                                        "GOOGLE_GEMINI",
                                        "gemini-3.1-flash-lite"
                                )
                );

        assertEquals(
                "AI reply draft must not exceed 2000 characters",
                exception.getMessage()
        );
    }

    private AiReplyDraftClientRequest createRequest(
            List<AiReplyDraftConversationMessage>
                    conversation
    ) {

        return new AiReplyDraftClientRequest(
                TicketType.INCIDENT,
                TicketPriority.HIGH,
                TicketStatus.IN_PROGRESS,
                "VPN unavailable",
                "Users cannot connect to VPN.",
                conversation
        );
    }
}