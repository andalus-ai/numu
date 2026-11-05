package tech.kayys.numou.advertising.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.advertising.domain.*;
import tech.kayys.numou.advertising.spi.AdvertisingAccountStore;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class AdvertisingAccountServiceTest {
    private static final TenantId TENANT=new TenantId("tenant-a");
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deniedAuthorizationPreventsAccountMutation() {
        InMemoryStore store=new InMemoryStore();
        AdvertisingAccountService service=new AdvertisingAccountService(store,(tenant,action)->CompletableFuture.completedFuture(false),
            party->CompletableFuture.completedFuture(true),CLOCK);

        assertThrows(CompletionException.class,()->service.registerAdvertiser(TENANT,new PartyReference("party-a"),"Advertiser").toCompletableFuture().join());
        assertTrue(store.advertisers.isEmpty());
    }

    @Test
    void inventoryLifecycleRequiresActivePublisherAndVerifiedProperty() {
        InMemoryStore store=new InMemoryStore();
        AdvertisingAccountService service=new AdvertisingAccountService(store,(tenant,action)->CompletableFuture.completedFuture(true),
            party->CompletableFuture.completedFuture(true),CLOCK);

        PublisherAccount publisher=service.registerPublisher(TENANT,new PartyReference("publisher-a"),"Publisher").toCompletableFuture().join();
        assertEquals(AccountStatus.PENDING,publisher.status());
        assertThrows(CompletionException.class,()->service.registerProperty(TENANT,publisher.id(),"Website",
            URI.create("https://publisher.example")).toCompletableFuture().join());

        publisher=service.activatePublisher(TENANT,publisher.id()).toCompletableFuture().join();
        PublisherProperty property=service.registerProperty(TENANT,publisher.id(),"Website",
            URI.create("https://publisher.example")).toCompletableFuture().join();
        assertEquals(PropertyStatus.PENDING_VERIFICATION,property.status());

        PublisherProperty verified=service.verifyProperty(TENANT,property.id()).toCompletableFuture().join();
        assertEquals(PropertyStatus.VERIFIED,verified.status());

        AdPlacement placement=service.definePlacement(TENANT,property.id(),"home.hero","display-banner").toCompletableFuture().join();
        assertEquals(PlacementStatus.DRAFT,placement.status());
        assertEquals(PlacementStatus.ENABLED,service.enablePlacement(TENANT,placement.id()).toCompletableFuture().join().status());
    }

    private static final class InMemoryStore implements AdvertisingAccountStore {
        private final Map<String,AdvertiserAccount> advertisers=new HashMap<>();
        private final Map<String,PublisherAccount> publishers=new HashMap<>();
        private final Map<String,PublisherProperty> properties=new HashMap<>();
        private final Map<String,AdPlacement> placements=new HashMap<>();
        private static String key(TenantId tenant,Object id){return tenant.value()+":"+id;}

        public CompletionStage<AdvertiserAccount> saveAdvertiser(AdvertiserAccount a){advertisers.put(key(a.tenantId(),a.id()),a);return CompletableFuture.completedFuture(a);}
        public CompletionStage<Optional<AdvertiserAccount>> findAdvertiser(TenantId t,AdvertisingAccountId id){return CompletableFuture.completedFuture(Optional.ofNullable(advertisers.get(key(t,id))));}
        public CompletionStage<PublisherAccount> savePublisher(PublisherAccount a){publishers.put(key(a.tenantId(),a.id()),a);return CompletableFuture.completedFuture(a);}
        public CompletionStage<Optional<PublisherAccount>> findPublisher(TenantId t,AdvertisingAccountId id){return CompletableFuture.completedFuture(Optional.ofNullable(publishers.get(key(t,id))));}
        public CompletionStage<PublisherProperty> saveProperty(PublisherProperty p){properties.put(key(p.tenantId(),p.id()),p);return CompletableFuture.completedFuture(p);}
        public CompletionStage<Optional<PublisherProperty>> findProperty(TenantId t,PublisherPropertyId id){return CompletableFuture.completedFuture(Optional.ofNullable(properties.get(key(t,id))));}
        public CompletionStage<AdPlacement> savePlacement(AdPlacement p){placements.put(key(p.tenantId(),p.id()),p);return CompletableFuture.completedFuture(p);}
        public CompletionStage<Optional<AdPlacement>> findPlacement(TenantId t,AdPlacementId id){return CompletableFuture.completedFuture(Optional.ofNullable(placements.get(key(t,id))));}
    }
}
