package tech.kayys.syirkah.identity.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

public record ChangeUserEmailCommand(
        UUID userId,
        String newEmail
) implements Command {

    public ChangeUserEmailCommand {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(newEmail, "newEmail cannot be null");
    }

}
