package tech.kayys.numou.support.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.support.domain.ticket.TicketId;
import tech.kayys.numou.support.domain.ticket.TicketPriority;

import java.util.Objects;

public record ChangeTicketPriorityCommand(
        TicketId ticketId,
        TicketPriority priority
) implements Command {

    public ChangeTicketPriorityCommand {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
    }
}