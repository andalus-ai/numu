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
  private final Clock clock;
  public AdvertisingAccountService(AdvertisingAccountStore store,AdvertisingAuthorizationPort authorization,Clock clock){
    this.store=Objects.requireNonNull(store); this.authorization=Objects.requireNonNull(authorization); this.clock=Objects.requireNonNull(clock);
  }
  public CompletionStage<AdvertiserAccount> registerAdvertiser(TenantId tenant,PartyReference party,String name){
    return authorized(tenant,"advertiser.register").thenCompose(v->store.saveAdvertiser(AdvertiserAccount.register(AdvertisingAccountId.generate(),tenant,party,name,clock.instant())));
  }
  public CompletionStage<PublisherAccount> registerPublisher(TenantId tenant,PartyReference party,String name){
    return authorized(tenant,"publisher.register").thenCompose(v->store.savePublisher(PublisherAccount.register(AdvertisingAccountId.generate(),tenant,party,name,clock.instant())));
  }
  public CompletionStage<PublisherProperty> registerProperty(TenantId tenant,AdvertisingAccountId publisher,String name,java.net.URI uri){
    return authorized(tenant,"publisher.inventory.manage").thenCompose(v->store.findPublisher(tenant,publisher)).thenCompose(found->{
      PublisherAccount account=found.orElseThrow(()->new IllegalArgumentException("publisher account not found"));
      if(account.status()!=AccountStatus.ACTIVE) throw new IllegalStateException("publisher account must be active");
      return store.saveProperty(PublisherProperty.register(PublisherPropertyId.generate(),tenant,publisher,name,uri,clock.instant()));
    });
  }
  public CompletionStage<AdPlacement> definePlacement(TenantId tenant,PublisherPropertyId property,String code,String format){
    return authorized(tenant,"publisher.inventory.manage").thenCompose(v->store.findProperty(tenant,property)).thenCompose(found->{
      PublisherProperty p=found.orElseThrow(()->new IllegalArgumentException("publisher property not found"));
      if(p.status()!=PropertyStatus.VERIFIED) throw new IllegalStateException("publisher property must be verified");
      return store.savePlacement(AdPlacement.define(AdPlacementId.generate(),tenant,property,code,format,clock.instant()));
    });
  }
  private CompletionStage<Void> authorized(TenantId tenant,String action){
    Objects.requireNonNull(tenant,"tenant");
    return authorization.mayManageAdvertising(tenant,action).thenCompose(allowed->allowed
      ? CompletableFuture.completedFuture(null)
      : CompletableFuture.failedFuture(new SecurityException("not authorized for "+action)));
  }
}
