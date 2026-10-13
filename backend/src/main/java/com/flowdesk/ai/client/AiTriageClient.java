package com.flowdesk.ai.client;

public interface AiTriageClient {

    AiTriageClientResult analyze(
            AiTriageClientRequest request
    );
}