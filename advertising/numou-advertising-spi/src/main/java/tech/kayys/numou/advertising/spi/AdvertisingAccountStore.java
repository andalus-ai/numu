package tech.kayys.numou.advertising.spi;
import tech.kayys.numou.advertising.domain.*;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
/** Tenant-scoped persistence contract. Implementations must enforce uniqueness and optimistic version checks in storage. */
public interface AdvertisingAccountStore {
  CompletionStage<AdvertiserAccount> saveAdvertiser(AdvertiserAccount account);
  CompletionStage<Optional<AdvertiserAccount>> findAdvertiser(TenantId tenantId,AdvertisingAccountId id);
  CompletionStage<PublisherAccount> savePublisher(PublisherAccount account);
  CompletionStage<Optional<PublisherAccount>> findPublisher(TenantId tenantId,AdvertisingAccountId id);
  CompletionStage<PublisherProperty> saveProperty(PublisherProperty property);
  CompletionStage<Optional<PublisherProperty>> findProperty(TenantId tenantId,PublisherPropertyId id);
  CompletionStage<AdPlacement> savePlacement(AdPlacement placement);
  CompletionStage<Optional<AdPlacement>> findPlacement(TenantId tenantId,AdPlacementId id);
}
