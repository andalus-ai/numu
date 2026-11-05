package tech.kayys.numou.advertising.spi;

import tech.kayys.numou.advertising.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped read model and append-only decision audit contract for ad serving. */
public interface AdvertisingAdServingStore {
  CompletionStage<Optional<AdServingInventory>> findInventory(TenantId tenantId,AdPlacementId placementId);
  CompletionStage<List<AdServingCandidate>> findEligibleCandidates(TenantId tenantId,AdPlacementId placementId,
                                                                    String format,Instant at);
  CompletionStage<AdDecision> recordDecision(AdDecision decision);
}
