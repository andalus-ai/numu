package tech.kayys.numou.advertising.spi;

import tech.kayys.numou.advertising.domain.*;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped persistence contract. Implementations enforce tenant filters and stable page ordering. */
public interface AdvertisingAccountStore {
  CompletionStage<AdvertiserAccount> saveAdvertiser(AdvertiserAccount account);
  CompletionStage<Optional<AdvertiserAccount>> findAdvertiser(TenantId tenantId,AdvertisingAccountId id);
  CompletionStage<AdvertisingPage<AdvertiserAccount>> listAdvertisers(TenantId tenantId,AdvertisingPageRequest page);

  CompletionStage<PublisherAccount> savePublisher(PublisherAccount account);
  CompletionStage<Optional<PublisherAccount>> findPublisher(TenantId tenantId,AdvertisingAccountId id);
  CompletionStage<AdvertisingPage<PublisherAccount>> listPublishers(TenantId tenantId,AdvertisingPageRequest page);

  CompletionStage<PublisherProperty> saveProperty(PublisherProperty property);
  CompletionStage<Optional<PublisherProperty>> findProperty(TenantId tenantId,PublisherPropertyId id);
  CompletionStage<AdvertisingPage<PublisherProperty>> listProperties(TenantId tenantId,AdvertisingAccountId publisherId,AdvertisingPageRequest page);

  CompletionStage<AdPlacement> savePlacement(AdPlacement placement);
  CompletionStage<Optional<AdPlacement>> findPlacement(TenantId tenantId,AdPlacementId id);
  CompletionStage<AdvertisingPage<AdPlacement>> listPlacements(TenantId tenantId,PublisherPropertyId propertyId,AdvertisingPageRequest page);
}
