package tech.kayys.numou.advertising.domain;

import java.time.Instant;
import java.util.Objects;

/** Explicit advertiser authorization to deliver a campaign into a publisher placement. */
public record CampaignPlacementTarget(TenantId tenantId,AdvertisingAccountId advertiserId,CampaignId campaignId,
                                      AdPlacementId placementId,Instant assignedAt) {
  public CampaignPlacementTarget {
    Objects.requireNonNull(tenantId);Objects.requireNonNull(advertiserId);Objects.requireNonNull(campaignId);
    Objects.requireNonNull(placementId);Objects.requireNonNull(assignedAt);
  }
}
