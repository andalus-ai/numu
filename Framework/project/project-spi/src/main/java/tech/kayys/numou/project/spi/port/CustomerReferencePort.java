package tech.kayys.numou.project.spi.port;

import java.util.UUID;

/**
 * Reference port to whichever bounded context owns customers.
 *
 * Project must never depend on the Customer/CRM module directly: the
 * application layer only asks "does this customer exist for this
 * tenant?" and the adapter decides whether that means the Numou
 * Ecosystem, an external CRM, or another microservice.
 */
public interface CustomerReferencePort {

    boolean exists(String tenantId, UUID customerId);
}
