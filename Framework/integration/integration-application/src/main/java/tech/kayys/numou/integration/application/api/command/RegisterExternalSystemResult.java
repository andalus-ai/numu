package tech.kayys.syirkah.integration.application.api.command;

import java.util.UUID;

/**
 * Result of registering a partner system.
 *
 * @param externalSystemId the new registration
 * @param status           always PENDING until the sandbox is verified
 */
public record RegisterExternalSystemResult(UUID externalSystemId, String status) {
}
