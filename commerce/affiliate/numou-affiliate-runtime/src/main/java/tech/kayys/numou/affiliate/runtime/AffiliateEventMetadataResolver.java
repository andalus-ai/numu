package tech.kayys.numou.affiliate.runtime;

import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.ClaimedEvent;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;

/**
 * Host-owned identity and audit-context bridge for the ecosystem event contract.
 *
 * <p>Affiliate tenant identifiers are opaque strings. The host must resolve them
 * through its authoritative tenant registry and return the corresponding UUID;
 * this adapter deliberately performs no parsing, hashing, or synthetic mapping.</p>
 */
@FunctionalInterface
public interface AffiliateEventMetadataResolver {
    EventMetadata resolve(ClaimedEvent event);
}