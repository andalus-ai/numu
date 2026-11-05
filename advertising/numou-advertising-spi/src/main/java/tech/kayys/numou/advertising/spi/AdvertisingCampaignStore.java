package tech.kayys.numou.advertising.spi;
import tech.kayys.numou.advertising.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
/** Tenant-scoped persistence contract for campaign, creative and their assignment relationship. */
public interface AdvertisingCampaignStore {
  CompletionStage<Campaign> saveCampaign(Campaign campaign);
  CompletionStage<Optional<Campaign>> findCampaign(TenantId tenantId,CampaignId id);
  CompletionStage<Creative> saveCreative(Creative creative);
  CompletionStage<Optional<Creative>> findCreative(TenantId tenantId,CreativeId id);
  CompletionStage<CampaignCreativeAssignment> assignCreative(CampaignCreativeAssignment assignment);
  CompletionStage<List<Creative>> findAssignedCreatives(TenantId tenantId,CampaignId campaignId);
}
