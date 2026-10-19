package tech.kayys.numou.support.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.domain.ticket.TicketId;

import java.util.Objects;

public record AssignTicketCommand(
        TicketId ticketId,
        UserId agentId
) implements Command {

    public AssignTicketCommand {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
        Objects.requireNonNull(agentId, "agentId cannot be null");
    }
}