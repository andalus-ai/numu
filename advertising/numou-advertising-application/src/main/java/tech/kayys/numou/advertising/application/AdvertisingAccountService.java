package tech.kayys.numou.advertising.application;

import tech.kayys.numou.advertising.domain.*;
import tech.kayys.numou.advertising.spi.AdvertisingAccountStore;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.*;

/** Use cases for onboarding advertiser/publisher accounts and registering publisher inventory. */
public final class AdvertisingAccountService {
  private final AdvertisingAccountStore store;
  private final AdvertisingAuthorizationPort authorization;
  private final AdvertisingPartyVerificationPort partyVerification;
  private final Clock clock;

  public AdvertisingAccountService(AdvertisingAccountStore store,AdvertisingAuthorizationPort authorization,
                                   AdvertisingPartyVerificationPort partyVerification,Clock clock){
    this.store=Objects.requireNonNull(store); this.authorization=Objects.requireNonNull(authorization);
    this.partyVerification=Objects.requireNonNull(partyVerification); this.clock=Objects.requireNonNull(clock);
  }

  public CompletionStage<AdvertiserAccount> registerAdvertiser(TenantId tenant,PartyReference party,String name){
    return authorized(tenant,"advertiser.register").thenCompose(v->activeParty(party)).thenCompose(v->store.saveAdvertiser(
      AdvertiserAccount.register(AdvertisingAccountId.generate(),tenant,party,name,clock.instant())));
  }

  public CompletionStage<PublisherAccount> registerPublisher(TenantId tenant,PartyReference party,String name){
    return authorized(tenant,"publisher.register").thenCompose(v->activeParty(party)).thenCompose(v->store.savePublisher(
      PublisherAccount.register(AdvertisingAccountId.generate(),tenant,party,name,clock.instant())));
  }

  public CompletionStage<AdvertiserAccount> activateAdvertiser(TenantId tenant,AdvertisingAccountId id){
    return authorized(tenant,"advertiser.activate").thenCompose(v->store.findAdvertiser(tenant,id))
      .thenCompose(found->store.saveAdvertiser(found.orElseThrow(()->new IllegalArgumentException("advertiser account not found")).activate()));
  }

  public CompletionStage<PublisherAccount> activatePublisher(TenantId tenant,AdvertisingAccountId id){
    return authorized(tenant,"publisher.activate").thenCompose(v->store.findPublisher(tenant,id))
      .thenCompose(found->store.savePublisher(found.orElseThrow(()->new IllegalArgumentException("publisher account not found")).activate()));
  }

  public CompletionStage<PublisherProperty> registerProperty(TenantId tenant,AdvertisingAccountId publisher,String name,java.net.URI uri){
    return authorized(tenant,"publisher.inventory.manage").thenCompose(v->store.findPublisher(tenant,publisher)).thenCompose(found->{
      PublisherAccount account=found.orElseThrow(()->new IllegalArgumentException("publisher account not found"));
      if(account.status()!=AccountStatus.ACTIVE) throw new IllegalStateException("publisher account must be active");
      return store.saveProperty(PublisherProperty.register(PublisherPropertyId.generate(),tenant,publisher,name,uri,clock.instant()));
    });
  }

  public CompletionStage<PublisherProperty> verifyProperty(TenantId tenant,PublisherPropertyId propertyId){
    return authorized(tenant,"publisher.inventory.verify").thenCompose(v->store.findProperty(tenant,propertyId))
      .thenCompose(found->{
        PublisherProperty property=found.orElseThrow(()->new IllegalArgumentException("publisher property not found"));
        return store.findPublisher(tenant,property.publisherId()).thenCompose(publisher->{
          PublisherAccount account=publisher.orElseThrow(()->new IllegalArgumentException("publisher account not found"));
          if(account.status()!=AccountStatus.ACTIVE) throw new IllegalStateException("publisher account must be active");
          return store.saveProperty(property.verify());
        });
      });
  }

  public CompletionStage<AdPlacement> definePlacement(TenantId tenant,PublisherPropertyId property,String code,String format){
    return authorized(tenant,"publisher.inventory.manage").thenCompose(v->store.findProperty(tenant,property)).thenCompose(found->{
      PublisherProperty p=found.orElseThrow(()->new IllegalArgumentException("publisher property not found"));
      if(p.status()!=PropertyStatus.VERIFIED) throw new IllegalStateException("publisher property must be verified");
      return store.savePlacement(AdPlacement.define(AdPlacementId.generate(),tenant,property,code,format,clock.instant()));
    });
  }

  public CompletionStage<AdPlacement> enablePlacement(TenantId tenant,AdPlacementId placementId){
    return authorized(tenant,"publisher.inventory.manage").thenCompose(v->store.findPlacement(tenant,placementId))
      .thenCompose(found->{
        AdPlacement placement=found.orElseThrow(()->new IllegalArgumentException("ad placement not found"));
        return store.findProperty(tenant,placement.propertyId()).thenCompose(property->{
          PublisherProperty p=property.orElseThrow(()->new IllegalArgumentException("publisher property not found"));
          if(p.status()!=PropertyStatus.VERIFIED) throw new IllegalStateException("publisher property must be verified");
          return store.findPublisher(tenant,p.publisherId()).thenCompose(publisher->{
            PublisherAccount account=publisher.orElseThrow(()->new IllegalArgumentException("publisher account not found"));
            if(account.status()!=AccountStatus.ACTIVE) throw new IllegalStateException("publisher account must be active");
            return store.savePlacement(placement.enable());
          });
        });
      });
  }

  public CompletionStage<Optional<AdvertiserAccount>> getAdvertiser(TenantId tenant,AdvertisingAccountId id){
    return authorized(tenant,"advertiser.read").thenCompose(v->store.findAdvertiser(tenant,id));
  }

  public CompletionStage<AdvertisingPage<AdvertiserAccount>> listAdvertisers(TenantId tenant,AdvertisingPageRequest page){
    return authorized(tenant,"advertiser.read").thenCompose(v->store.listAdvertisers(tenant,page));
  }

  public CompletionStage<Optional<PublisherAccount>> getPublisher(TenantId tenant,AdvertisingAccountId id){
    return authorized(tenant,"publisher.read").thenCompose(v->store.findPublisher(tenant,id));
  }

  public CompletionStage<AdvertisingPage<PublisherAccount>> listPublishers(TenantId tenant,AdvertisingPageRequest page){
    return authorized(tenant,"publisher.read").thenCompose(v->store.listPublishers(tenant,page));
  }

  public CompletionStage<Optional<PublisherProperty>> getProperty(TenantId tenant,PublisherPropertyId id){
    return authorized(tenant,"publisher.inventory.read").thenCompose(v->store.findProperty(tenant,id));
  }

  public CompletionStage<AdvertisingPage<PublisherProperty>> listProperties(TenantId tenant,AdvertisingAccountId publisherId,AdvertisingPageRequest page){
    return authorized(tenant,"publisher.inventory.read").thenCompose(v->store.findPublisher(tenant,publisherId))
      .thenCompose(found->{
        found.orElseThrow(()->new IllegalArgumentException("publisher account not found"));
        return store.listProperties(tenant,publisherId,page);
      });
  }

  public CompletionStage<Optional<AdPlacement>> getPlacement(TenantId tenant,AdPlacementId id){
    return authorized(tenant,"publisher.inventory.read").thenCompose(v->store.findPlacement(tenant,id));
  }

  public CompletionStage<AdvertisingPage<AdPlacement>> listPlacements(TenantId tenant,PublisherPropertyId propertyId,AdvertisingPageRequest page){
    return authorized(tenant,"publisher.inventory.read").thenCompose(v->store.findProperty(tenant,propertyId))
      .thenCompose(found->{
        found.orElseThrow(()->new IllegalArgumentException("publisher property not found"));
        return store.listPlacements(tenant,propertyId,page);
      });
  }

  private CompletionStage<Void> activeParty(PartyReference party) {
    Objects.requireNonNull(party,"party");
    return partyVerification.isActiveParty(party).thenCompose(active->active
      ? CompletableFuture.completedFuture(null)
      : CompletableFuture.failedFuture(new IllegalArgumentException("party reference must resolve to an active canonical Party")));
  }

  private CompletionStage<Void> authorized(TenantId tenant,String action){
    Objects.requireNonNull(tenant,"tenant");
    return authorization.mayManageAdvertising(tenant,action).thenCompose(allowed->allowed
      ? CompletableFuture.completedFuture(null)
      : CompletableFuture.failedFuture(new SecurityException("not authorized for "+action)));
  }
}
