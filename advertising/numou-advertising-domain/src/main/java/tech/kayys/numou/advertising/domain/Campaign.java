package tech.kayys.numou.advertising.domain;

import java.time.Instant;
import java.util.Objects;

/** Advertiser-owned campaign configuration. Delivery and spend are deliberately outside this aggregate. */
public record Campaign(CampaignId id, TenantId tenantId, AdvertisingAccountId advertiserId,
                       String name, CampaignObjective objective, Instant startsAt, Instant endsAt,
                       CampaignStatus status, Instant createdAt, long version) {
  public Campaign {
    Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(advertiserId);
    name=AdvertiserAccount.required(name,"campaign name");
    Objects.requireNonNull(objective); Objects.requireNonNull(startsAt);
    Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
    if(endsAt!=null && !endsAt.isAfter(startsAt)) throw new IllegalArgumentException("campaign end must be after start");
    if(version<0) throw new IllegalArgumentException("version cannot be negative");
  }
  public static Campaign draft(CampaignId id,TenantId tenant,AdvertisingAccountId advertiser,String name,
                               CampaignObjective objective,Instant startsAt,Instant endsAt,Instant now) {
    return new Campaign(id,tenant,advertiser,name,objective,startsAt,endsAt,CampaignStatus.DRAFT,now,0);
  }
  public Campaign schedule() {
    if(status!=CampaignStatus.DRAFT) throw new IllegalStateException("only draft campaigns can be scheduled");
    return next(CampaignStatus.SCHEDULED);
  }
  public Campaign activate(Instant now) {
    Objects.requireNonNull(now);
    if(status!=CampaignStatus.SCHEDULED && status!=CampaignStatus.PAUSED)
      throw new IllegalStateException("only scheduled or paused campaigns can be activated");
    if(now.isBefore(startsAt) || (endsAt!=null && !now.isBefore(endsAt)))
      throw new IllegalStateException("campaign is outside its active window");
    return next(CampaignStatus.ACTIVE);
  }
  public Campaign pause() {
    if(status!=CampaignStatus.ACTIVE && status!=CampaignStatus.SCHEDULED)
      throw new IllegalStateException("only active or scheduled campaigns can be paused");
    return next(CampaignStatus.PAUSED);
  }
  public Campaign complete(Instant now) {
    Objects.requireNonNull(now);
    if(status!=CampaignStatus.ACTIVE && status!=CampaignStatus.PAUSED)
      throw new IllegalStateException("only active or paused campaigns can be completed");
    if(endsAt!=null && now.isBefore(endsAt))
      throw new IllegalStateException("campaign window has not ended");
    return next(CampaignStatus.COMPLETED);
  }
  public Campaign archive() {
    if(status==CampaignStatus.ACTIVE) throw new IllegalStateException("active campaigns cannot be archived");
    if(status==CampaignStatus.ARCHIVED) return this;
    return next(CampaignStatus.ARCHIVED);
  }
  private Campaign next(CampaignStatus next) {
    if(status==CampaignStatus.ARCHIVED || status==CampaignStatus.COMPLETED)
      throw new IllegalStateException("terminal campaign cannot transition");
    return new Campaign(id,tenantId,advertiserId,name,objective,startsAt,endsAt,next,createdAt,version+1);
  }
}
