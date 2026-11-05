package tech.kayys.numou.advertising.application;

import tech.kayys.numou.advertising.domain.*;
import tech.kayys.numou.advertising.spi.AdvertisingAccountStore;
import tech.kayys.numou.advertising.spi.AdvertisingCampaignStore;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;

/** Campaign and creative management use cases. Tenant identity must come from the trusted host security context. */
public final class AdvertisingCampaignService {
  private final AdvertisingAccountStore accounts;
  private final AdvertisingCampaignStore campaigns;
  private final AdvertisingAuthorizationPort authorization;
  private final Clock clock;

  public AdvertisingCampaignService(AdvertisingAccountStore accounts,AdvertisingCampaignStore campaigns,
                                    AdvertisingAuthorizationPort authorization,Clock clock) {
    this.accounts=Objects.requireNonNull(accounts); this.campaigns=Objects.requireNonNull(campaigns);
    this.authorization=Objects.requireNonNull(authorization); this.clock=Objects.requireNonNull(clock);
  }

  public CompletionStage<Campaign> createCampaign(TenantId tenant,AdvertisingAccountId advertiser,String name,
                                                    CampaignObjective objective,Instant startsAt,Instant endsAt) {
    return authorized(tenant,"campaign.create").thenCompose(v->activeAdvertiser(tenant,advertiser))
      .thenCompose(v->campaigns.saveCampaign(Campaign.draft(CampaignId.generate(),tenant,advertiser,name,objective,
        startsAt,endsAt,clock.instant())));
  }

  public CompletionStage<Campaign> scheduleCampaign(TenantId tenant,CampaignId id) {
    return authorized(tenant,"campaign.manage").thenCompose(v->campaigns.findCampaign(tenant,id))
      .thenCompose(found->campaigns.saveCampaign(found.orElseThrow(()->new IllegalArgumentException("campaign not found")).schedule()));
  }

  public CompletionStage<Campaign> activateCampaign(TenantId tenant,CampaignId id) {
    return authorized(tenant,"campaign.manage").thenCompose(v->campaigns.findCampaign(tenant,id))
      .thenCompose(found->{
        Campaign campaign=found.orElseThrow(()->new IllegalArgumentException("campaign not found"));
        return activeAdvertiser(tenant,campaign.advertiserId()).thenCompose(v->campaigns.saveCampaign(campaign.activate(clock.instant())));
      });
  }

  public CompletionStage<Campaign> pauseCampaign(TenantId tenant,CampaignId id) {
    return authorized(tenant,"campaign.manage").thenCompose(v->campaigns.findCampaign(tenant,id))
      .thenCompose(found->campaigns.saveCampaign(found.orElseThrow(()->new IllegalArgumentException("campaign not found")).pause()));
  }

  public CompletionStage<Creative> createCreative(TenantId tenant,AdvertisingAccountId advertiser,String name,
                                                    CreativeFormat format,String headline,String body,URI destination,
                                                    String documentReference) {
    return authorized(tenant,"creative.create").thenCompose(v->activeAdvertiser(tenant,advertiser))
      .thenCompose(v->campaigns.saveCreative(Creative.draft(CreativeId.generate(),tenant,advertiser,name,format,
        headline,body,destination,documentReference,clock.instant())));
  }

  public CompletionStage<Creative> submitCreative(TenantId tenant,CreativeId id) {
    return authorized(tenant,"creative.submit").thenCompose(v->campaigns.findCreative(tenant,id))
      .thenCompose(found->campaigns.saveCreative(found.orElseThrow(()->new IllegalArgumentException("creative not found")).submit()));
  }

  public CompletionStage<Creative> approveCreative(TenantId tenant,CreativeId id) {
    return authorized(tenant,"creative.review").thenCompose(v->campaigns.findCreative(tenant,id))
      .thenCompose(found->campaigns.saveCreative(found.orElseThrow(()->new IllegalArgumentException("creative not found")).approve()));
  }

  public CompletionStage<Creative> rejectCreative(TenantId tenant,CreativeId id,String reason) {
    return authorized(tenant,"creative.review").thenCompose(v->campaigns.findCreative(tenant,id))
      .thenCompose(found->campaigns.saveCreative(found.orElseThrow(()->new IllegalArgumentException("creative not found")).reject(reason)));
  }

  public CompletionStage<CampaignCreativeAssignment> assignCreative(TenantId tenant,CampaignId campaignId,CreativeId creativeId) {
    return authorized(tenant,"campaign.manage").thenCompose(v->campaigns.findCampaign(tenant,campaignId))
      .thenCompose(campaignFound->{
        Campaign campaign=campaignFound.orElseThrow(()->new IllegalArgumentException("campaign not found"));
        if(campaign.status()!=CampaignStatus.DRAFT && campaign.status()!=CampaignStatus.SCHEDULED)
          throw new IllegalStateException("creatives can only be assigned before campaign activation");
        return campaigns.findCreative(tenant,creativeId).thenCompose(creativeFound->{
          Creative creative=creativeFound.orElseThrow(()->new IllegalArgumentException("creative not found"));
          if(!campaign.advertiserId().equals(creative.advertiserId()))
            throw new IllegalArgumentException("campaign and creative must belong to the same advertiser");
          if(creative.status()!=CreativeStatus.APPROVED)
            throw new IllegalStateException("only approved creatives can be assigned");
          return campaigns.assignCreative(new CampaignCreativeAssignment(tenant,campaignId,creativeId,clock.instant()));
        });
      });
  }

  private CompletionStage<Void> activeAdvertiser(TenantId tenant,AdvertisingAccountId id) {
    return accounts.findAdvertiser(tenant,id).thenCompose(found->{
      AdvertiserAccount account=found.orElseThrow(()->new IllegalArgumentException("advertiser account not found"));
      if(account.status()!=AccountStatus.ACTIVE) throw new IllegalStateException("advertiser account must be active");
      return CompletableFuture.completedFuture(null);
    });
  }

  private CompletionStage<Void> authorized(TenantId tenant,String action) {
    Objects.requireNonNull(tenant,"tenant");
    return authorization.mayManageAdvertising(tenant,action).thenCompose(allowed->allowed
      ? CompletableFuture.completedFuture(null)
      : CompletableFuture.failedFuture(new SecurityException("not authorized for "+action)));
  }
}
