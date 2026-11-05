package tech.kayys.numou.advertising.application;

import tech.kayys.numou.advertising.domain.*;
import tech.kayys.numou.advertising.spi.AdvertisingAdServingStore;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Serving eligibility and selection use case. It does not create billable impressions:
 * measurement and billable-event qualification belong to M4.
 */
public final class AdvertisingAdServingService {
  static final int MAX_CANDIDATES_PER_DECISION=100;
  private final AdvertisingAdServingStore store;
  private final Clock clock;

  public AdvertisingAdServingService(AdvertisingAdServingStore store,Clock clock) {
    this.store=Objects.requireNonNull(store);this.clock=Objects.requireNonNull(clock);
  }

  public CompletionStage<AdServingResult> serve(AdServingRequest request) {
    Objects.requireNonNull(request,"request");
    Instant now=clock.instant();
    return store.findInventory(request.tenantId(),request.placementId()).thenCompose(found->{
      if(found.isEmpty()) return noFill(request,null,AdDecisionReason.PLACEMENT_NOT_FOUND,now);
      AdServingInventory inventory=found.get();
      if(!inventory.placement().tenantId().equals(request.tenantId()))
        return noFill(request,null,AdDecisionReason.INVENTORY_NOT_ELIGIBLE,now);
      if(inventory.placement().status()!=PlacementStatus.ENABLED ||
         inventory.property().status()!=PropertyStatus.VERIFIED ||
         inventory.publisher().status()!=AccountStatus.ACTIVE)
        return noFill(request,inventory,AdDecisionReason.INVENTORY_NOT_ELIGIBLE,now);
      if(!sameOrigin(inventory.property().canonicalUri(),request.pageUri()))
        return noFill(request,inventory,AdDecisionReason.PROPERTY_ORIGIN_MISMATCH,now);
      if(!inventory.placement().format().equalsIgnoreCase(request.format()))
        return noFill(request,inventory,AdDecisionReason.FORMAT_MISMATCH,now);

      return store.findEligibleCandidates(request.tenantId(),request.placementId(),request.format(),now,MAX_CANDIDATES_PER_DECISION)
        .thenCompose(candidates->{
          List<AdServingCandidate> eligible=candidates.stream()
            .filter(candidate->eligible(candidate,request,now))
            .sorted(Comparator.comparing((AdServingCandidate c)->c.campaign().createdAt())
              .thenComparing(c->c.campaign().id().value())
              .thenComparing(c->c.creative().id().value()))
            .limit(MAX_CANDIDATES_PER_DECISION)
            .toList();
          if(eligible.isEmpty()) return noFill(request,inventory,AdDecisionReason.NO_ELIGIBLE_CANDIDATE,now);
          AdServingCandidate selected=eligible.get(0);
          AdDecision decision=AdDecision.filled(request.tenantId(),inventory,selected,now);
          return store.recordDecision(decision).thenApply(saved->AdServingResult.filled(saved,selected.creative()));
        });
    });
  }

  private CompletionStage<AdServingResult> noFill(AdServingRequest request,AdServingInventory inventory,
                                                    AdDecisionReason reason,Instant now) {
    AdDecision decision=AdDecision.noFill(request.tenantId(),request.placementId(),
      inventory==null?null:inventory.property().id(),reason,now);
    return store.recordDecision(decision).thenApply(AdServingResult::noFill);
  }

  private static boolean eligible(AdServingCandidate c,AdServingRequest request,Instant now) {
    Campaign campaign=c.campaign();Creative creative=c.creative();AdvertiserAccount advertiser=c.advertiser();
    return campaign.tenantId().equals(request.tenantId()) &&
      campaign.status()==CampaignStatus.ACTIVE &&
      !now.isBefore(campaign.startsAt()) && (campaign.endsAt()==null || now.isBefore(campaign.endsAt())) &&
      advertiser.status()==AccountStatus.ACTIVE &&
      creative.status()==CreativeStatus.APPROVED &&
      creative.format().name().equalsIgnoreCase(request.format());
  }

  private static boolean sameOrigin(URI property,URI page) {
    return property.getScheme().equalsIgnoreCase(page.getScheme()) &&
      property.getHost().equalsIgnoreCase(page.getHost()) &&
      effectivePort(property)==effectivePort(page);
  }

  private static int effectivePort(URI uri) {
    if(uri.getPort()>=0) return uri.getPort();
    return "https".equalsIgnoreCase(uri.getScheme())?443:80;
  }
}
