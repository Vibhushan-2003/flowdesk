package com.flowdesk.ai.tool;

import com.flowdesk.sla.domain.SlaStatus;

public record AiSupportCopilotSlaSnapshot(

        SlaStatus responseStatus,

        SlaStatus resolutionStatus,

        Long responseMinutesUntilDue,

        Long resolutionMinutesUntilDue

) {
}