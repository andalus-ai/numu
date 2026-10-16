package tech.kayys.syirkah.identity.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;

public record RegisterUserCommand(
        String email,
        String rawPassword,
        String displayName
) implements Command {

    public RegisterUserCommand {
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(rawPassword, "rawPassword cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
    }

}
