package tech.kayys.numou.advertising.domain;
import java.util.Objects;
import java.util.UUID;
public record CampaignId(UUID value) {
  public CampaignId { Objects.requireNonNull(value); }
  public static CampaignId generate(){ return new CampaignId(UUID.randomUUID()); }
}
