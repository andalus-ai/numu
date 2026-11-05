package tech.kayys.numou.advertising.postgres;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.advertising.domain.*;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgresAdvertisingAccountStoreTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private PostgresAdvertisingAccountStore store;
    private TenantId tenantA;
    private TenantId tenantB;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        tenantA=new TenantId("tenant-a");
        tenantB=new TenantId("tenant-b");
        now=Instant.parse("2026-11-05T00:00:00Z");
        DataSource ds=dataSource();
        try(Connection c=ds.getConnection(); Statement s=c.createStatement();
            InputStream migration=getClass().getResourceAsStream("/db/migration/V1__advertising_accounts_and_inventory.sql")) {
            assertNotNull(migration,"advertising schema migration must be packaged");
            String sql=new String(migration.readAllBytes(), StandardCharsets.UTF_8);
            for(String command:sql.split(";")) if(!command.isBlank()) s.execute(command);
        }
        store=new PostgresAdvertisingAccountStore(ds);
    }

    @Test
    void persistsAccountsAndScopesReadsByTenant() {
        var advertiser=AdvertiserAccount.register(AdvertisingAccountId.generate(),tenantA,new PartyReference("party-a"),"Advertiser A",now);
        store.saveAdvertiser(advertiser).toCompletableFuture().join();
        assertTrue(store.findAdvertiser(tenantA,advertiser.id()).toCompletableFuture().join().isPresent());
        assertTrue(store.findAdvertiser(tenantB,advertiser.id()).toCompletableFuture().join().isEmpty());

        var publisher=PublisherAccount.register(AdvertisingAccountId.generate(),tenantA,new PartyReference("publisher-a"),"Publisher A",now).activate();
        store.savePublisher(new PublisherAccount(publisher.id(),tenantA,publisher.party(),publisher.displayName(),
            AccountStatus.PENDING,now,0)).toCompletableFuture().join();
        assertTrue(store.findPublisher(tenantA,publisher.id()).toCompletableFuture().join().isPresent());
        assertTrue(store.findPublisher(tenantB,publisher.id()).toCompletableFuture().join().isEmpty());
    }

    @Test
    void updatesUseOptimisticVersionAndRejectStaleWriters() {
        var pending=AdvertiserAccount.register(AdvertisingAccountId.generate(),tenantA,new PartyReference("party-version"),"Versioned",now);
        store.saveAdvertiser(pending).toCompletableFuture().join();
        var active=pending.activate();
        store.saveAdvertiser(active).toCompletableFuture().join();

        var stale=pending.suspend();
        assertThrows(RuntimeException.class,()->store.saveAdvertiser(stale).toCompletableFuture().join());
        var loaded=store.findAdvertiser(tenantA,pending.id()).toCompletableFuture().join().orElseThrow();
        assertEquals(AccountStatus.ACTIVE,loaded.status());
        assertEquals(1,loaded.version());
    }

    @Test
    void enforcesTenantScopedUniquenessAndInventoryForeignKeys() {
        var publisher=PublisherAccount.register(AdvertisingAccountId.generate(),tenantA,new PartyReference("publisher-unique"),"Publisher",now);
        store.savePublisher(publisher).toCompletableFuture().join();
        var duplicate=PublisherAccount.register(AdvertisingAccountId.generate(),tenantA,new PartyReference("publisher-unique"),"Duplicate",now);
        assertThrows(RuntimeException.class,()->store.savePublisher(duplicate).toCompletableFuture().join());

        var property=PublisherProperty.register(PublisherPropertyId.generate(),tenantA,publisher.id(),"Main site",URI.create("https://publisher.example"),now);
        store.saveProperty(property).toCompletableFuture().join();
        assertTrue(store.findProperty(tenantB,property.id()).toCompletableFuture().join().isEmpty());

        var placement=AdPlacement.define(AdPlacementId.generate(),tenantA,property.id(),"home.hero","display-banner",now);
        store.savePlacement(placement).toCompletableFuture().join();
        assertTrue(store.findPlacement(tenantA,placement.id()).toCompletableFuture().join().isPresent());
        assertTrue(store.findPlacement(tenantB,placement.id()).toCompletableFuture().join().isEmpty());

        var invalidProperty=PublisherProperty.register(PublisherPropertyId.generate(),tenantB,
            new AdvertisingAccountId(UUID.randomUUID()),"Cross tenant",URI.create("https://other.example"),now);
        assertThrows(RuntimeException.class,()->store.saveProperty(invalidProperty).toCompletableFuture().join());
    }

    @Test
    void rejectsDuplicatePlacementCodeWithinOneProperty() {
        var publisher=PublisherAccount.register(AdvertisingAccountId.generate(),tenantA,new PartyReference("publisher-placements"),"Publisher",now);
        store.savePublisher(publisher).toCompletableFuture().join();
        var property=PublisherProperty.register(PublisherPropertyId.generate(),tenantA,publisher.id(),"Site",URI.create("https://site.example"),now);
        store.saveProperty(property).toCompletableFuture().join();
        store.savePlacement(AdPlacement.define(AdPlacementId.generate(),tenantA,property.id(),"header","display",now)).toCompletableFuture().join();
        var duplicate=AdPlacement.define(AdPlacementId.generate(),tenantA,property.id(),"header","native",now);
        assertThrows(RuntimeException.class,()->store.savePlacement(duplicate).toCompletableFuture().join());
    }

    private static DataSource dataSource() {
        PGSimpleDataSource source=new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }
}
