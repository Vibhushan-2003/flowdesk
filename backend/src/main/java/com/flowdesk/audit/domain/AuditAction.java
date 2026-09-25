package com.flowdesk.audit.domain;

public enum AuditAction {

    TICKET_CREATED,

    TICKET_CLAIMED,

    TICKET_STATUS_CHANGED,

    SLA_RESPONSE_BREACHED,

    SLA_RESOLUTION_BREACHED
}