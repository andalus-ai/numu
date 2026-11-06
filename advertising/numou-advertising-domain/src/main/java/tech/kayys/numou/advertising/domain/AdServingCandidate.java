package tech.kayys.numou.advertising.domain;

import java.util.Objects;

/** Fully joined candidate snapshot; the serving engine rechecks all delivery invariants before selection. */
public record AdServingCandidate(Campaign campaign,AdvertiserAccount advertiser,Creative creative) {
  public AdServingCandidate {
    Objects.requireNonNull(campaign);Objects.requireNonNull(advertiser);Objects.requireNonNull(creative);
    if(!campaign.tenantId().equals(advertiser.tenantId()) || !advertiser.tenantId().equals(creative.tenantId()) ||
       !campaign.advertiserId().equals(advertiser.id()) || !campaign.advertiserId().equals(creative.advertiserId()))
      throw new IllegalArgumentException("candidate campaign, advertiser and creative ownership must match");
  }
}
