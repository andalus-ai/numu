package tech.kayys.numou.support.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.support.domain.ticket.TicketId;

import java.util.Objects;

public record AddTicketCommentCommand(
        TicketId ticketId,
        UserId authorId,
        String body,
        boolean internal
) implements Command {

    public AddTicketCommentCommand {
        Objects.requireNonNull(ticketId, "ticketId cannot be null");
        Objects.requireNonNull(authorId, "authorId cannot be null");
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body cannot be blank");
        }
    }
}