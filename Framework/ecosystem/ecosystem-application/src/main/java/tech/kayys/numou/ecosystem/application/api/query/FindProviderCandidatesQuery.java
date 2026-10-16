package tech.kayys.syirkah.ecosystem.application.api.query;

import tech.kayys.syirkah.foundation.application.query.Query;

import java.util.Objects;

/**
 * Asks "who can supply this capability?" without naming any provider.
 *
 * @param capabilityCode dotted capability code, e.g. logistics.transportation
 * @param coverageFilter optional required coverage; blank means "any"
 */
public record FindProviderCandidatesQuery(
        String capabilityCode,
        String coverageFilter) implements Query {

    public FindProviderCandidatesQuery {
        Objects.requireNonNull(capabilityCode, "capabilityCode cannot be null");
    }

    public static FindProviderCandidatesQuery forCapability(String capabilityCode) {
        return new FindProviderCandidatesQuery(capabilityCode, "");
    }
}
