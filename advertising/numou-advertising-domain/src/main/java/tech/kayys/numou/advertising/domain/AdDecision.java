package tech.kayys.numou.advertising.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable audit outcome for every serving request, including no-fill decisions. */
public record AdDecision(UUID id,TenantId tenantId,AdPlacementId placementId,PublisherPropertyId propertyId,
                         CampaignId campaignId,CreativeId creativeId,AdDecisionStatus status,
                         AdDecisionReason reason,Instant decidedAt) {
  public AdDecision {
    Objects.requireNonNull(id);Objects.requireNonNull(tenantId);Objects.requireNonNull(status);
    Objects.requireNonNull(reason);Objects.requireNonNull(decidedAt);
    if(status==AdDecisionStatus.FILLED && (placementId==null || propertyId==null || campaignId==null || creativeId==null || reason!=AdDecisionReason.SELECTED))
      throw new IllegalArgumentException("filled decision requires placement, property, campaign, creative and SELECTED reason");
    if(status==AdDecisionStatus.NO_FILL && (campaignId!=null || creativeId!=null || reason==AdDecisionReason.SELECTED))
      throw new IllegalArgumentException("no-fill decision cannot reference a selected campaign or creative");
  }
  public static AdDecision filled(TenantId tenant,AdServingInventory inventory,AdServingCandidate candidate,Instant now) {
    return new AdDecision(UUID.randomUUID(),tenant,inventory.placement().id(),inventory.property().id(),
      candidate.campaign().id(),candidate.creative().id(),AdDecisionStatus.FILLED,AdDecisionReason.SELECTED,now);
  }
  public static AdDecision noFill(TenantId tenant,AdPlacementId placement,PublisherPropertyId property,AdDecisionReason reason,Instant now) {
    return new AdDecision(UUID.randomUUID(),tenant,placement,property,null,null,AdDecisionStatus.NO_FILL,reason,now);
  }
}
