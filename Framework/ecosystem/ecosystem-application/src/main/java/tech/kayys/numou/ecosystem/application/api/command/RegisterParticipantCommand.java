package tech.kayys.syirkah.ecosystem.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.ecosystem.domain.valueobject.ParticipantType;

import java.util.Objects;
import java.util.UUID;

/**
 * Registers a participant in the Numou ecosystem (base01.md P1-06).
 *
 * @param code           stable, unique ecosystem handle
 * @param name           display / trading name
 * @param type           how the participant relates to the platform
 * @param organizationId optional owning Organization aggregate
 * @param tenantId       optional owning tenant (absent for external providers)
 */
public record RegisterParticipantCommand(
        String code,
        String name,
        ParticipantType type,
        UUID organizationId,
        UUID tenantId) implements Command {

    public RegisterParticipantCommand {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }
}
