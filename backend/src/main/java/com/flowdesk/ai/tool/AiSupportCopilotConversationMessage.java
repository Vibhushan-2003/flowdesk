package com.flowdesk.ai.tool;

public record AiSupportCopilotConversationMessage(

        Role role,

        String body

) {

    public enum Role {

        REQUESTER,

        SUPPORT
    }
}