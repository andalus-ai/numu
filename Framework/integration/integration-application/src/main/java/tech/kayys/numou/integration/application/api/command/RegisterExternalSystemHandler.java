package tech.kayys.syirkah.integration.application.api.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.integration.application.port.ExternalSystemRepository;
import tech.kayys.syirkah.integration.domain.identifier.ExternalSystemId;
import tech.kayys.syirkah.integration.domain.model.ExternalSystem;

import java.util.Objects;

/**
 * Registers a partner system and declares the capabilities it exposes.
 *
 * <p>Reacts with {@link Result} rather than exceptions: an invalid or
 * unauthenticated integration request is an expected business outcome,
 * not a programming error.
 */
public final class RegisterExternalSystemHandler
        implements CommandHandler<RegisterExternalSystemCommand, Result<RegisterExternalSystemResult>> {

    private final ExternalSystemRepository externalSystems;

    public RegisterExternalSystemHandler(ExternalSystemRepository externalSystems) {
        this.externalSystems = Objects.requireNonNull(externalSystems, "externalSystems cannot be null");
    }

    @Override
    public Uni<Result<RegisterExternalSystemResult>> handle(RegisterExternalSystemCommand command) {
        final var system = ExternalSystem.register(
                ExternalSystemId.generate(),
                command.participantId(),
                command.name(),
                command.type(),
                command.direction(),
                command.authScheme(),
                command.baseUrl(),
                command.credentialHandle());

        command.capabilityCodes().forEach(system::exposeCapability);

        final var request = command;
        return externalSystems.save(system)
                .map(saved -> Result.success(new RegisterExternalSystemResult(
                        saved.getId().value(),
                        saved.getStatus().name())))
                .onFailure()
                .recoverWithItem(error -> Result.failure(
                        tech.kayys.syirkah.foundation.application.result.ApplicationError.of(
                                "INTEGRATION_EXTERNAL_SYSTEM_REGISTER_FAILED",
                                "Could not register external system '" + request.name()
                                        + "': " + error.getMessage())));
    }
}
