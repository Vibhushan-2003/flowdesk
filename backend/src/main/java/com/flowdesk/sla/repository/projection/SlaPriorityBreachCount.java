package com.flowdesk.sla.repository.projection;

import com.flowdesk.ticket.domain.TicketPriority;

public interface SlaPriorityBreachCount {

    TicketPriority getPriority();

    long getBreachCount();
}
