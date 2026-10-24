package tech.kayys.numou.omnichannel.core;

import java.util.Optional;

/** Loads verified metadata only when the supplied work item's lease is still current. */
public interface InboundVerifiedEventMetadataStore {
    Optional<InboundVerifiedEventMetadata> findForLease(InboundWorkItem item);
}
