package com.flowdesk.ai.client;

public interface AiReplyDraftClient {

    AiReplyDraftClientResult generateDraft(
            AiReplyDraftClientRequest request
    );
}