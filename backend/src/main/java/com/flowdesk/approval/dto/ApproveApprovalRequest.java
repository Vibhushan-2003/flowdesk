package com.flowdesk.approval.dto;

import jakarta.validation.constraints.Size;

public record ApproveApprovalRequest(

        @Size(
                max = 500,
                message = "Approval note must not exceed 500 characters"
        )
        String note
) {
}