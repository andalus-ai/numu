package tech.kayys.numou.advertising.domain;
import java.time.Instant;
import java.util.Objects;
public record CampaignCreativeAssignment(TenantId tenantId,CampaignId campaignId,CreativeId creativeId,Instant assignedAt) {
  public CampaignCreativeAssignment {
    Objects.requireNonNull(tenantId); Objects.requireNonNull(campaignId); Objects.requireNonNull(creativeId); Objects.requireNonNull(assignedAt);
  }
}
