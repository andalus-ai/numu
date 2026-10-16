package tech.kayys.numou.integration.application.api.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.integration.domain.valueobject.AuthScheme;
import tech.kayys.numou.integration.domain.valueobject.ExternalSystemType;
import tech.kayys.numou.integration.domain.valueobject.IntegrationDirection;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Registers a partner system with Numou (base01.md §P1-16).
 */
public record RegisterExternalSystemCommand(
        UUID participantId,
        String name,
        ExternalSystemType type,
        IntegrationDirection direction,
        AuthScheme authScheme,
        String baseUrl,
        String credentialHandle,
        Set<String> capabilityCodes) implements Command {

    public RegisterExternalSystemCommand {
        Objects.requireNonNull(participantId, "participantId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(direction, "direction cannot be null");
        Objects.requireNonNull(authScheme, "authScheme cannot be null");
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        capabilityCodes = capabilityCodes == null ? Set.of() : Set.copyOf(capabilityCodes);
    }
}
