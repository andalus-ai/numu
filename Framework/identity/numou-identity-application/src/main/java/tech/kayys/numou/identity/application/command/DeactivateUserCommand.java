package tech.kayys.syirkah.identity.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

public record DeactivateUserCommand(
        UUID userId,
        String reason
) implements Command {

    public DeactivateUserCommand {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }

}
