package tech.kayys.numou.foundation.domain.time;

import java.time.Instant;

/**
 * Abstraction over "now" so domain/application code never calls
 * Instant.now() directly, which makes time-dependent behavior
 * (subscription renewal, accounting periods, promotion expiry,
 * inventory expiry, enrollment/deadline windows) trivially testable.
 *
 * Only the interface lives here - a SystemDomainClock implementation
 * belongs to the infrastructure/bootstrap layer of each service.
 */
public interface DomainClock {

    Instant now();

}
