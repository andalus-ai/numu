package tech.kayys.numou.support.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.support.domain.ticket.TicketPriority;
import tech.kayys.numou.support.domain.ticket.TicketType;

import java.util.Objects;

public record CreatePortalTicketCommand(
        TicketType type,
        TicketPriority priority,
        String subject,
        String description
) implements Command {

    public CreatePortalTicketCommand {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject cannot be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description cannot be blank");
        }
    }
}
