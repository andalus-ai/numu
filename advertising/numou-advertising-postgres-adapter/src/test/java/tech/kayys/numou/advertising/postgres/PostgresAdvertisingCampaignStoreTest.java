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
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgresAdvertisingCampaignStoreTest {
  @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
  private TenantId tenantA,tenantB;
  private Instant now;
  private PostgresAdvertisingAccountStore accountStore;
  private PostgresAdvertisingCampaignStore campaignStore;

  @BeforeEach void setUp() throws Exception {
    tenantA=new TenantId("tenant-a");tenantB=new TenantId("tenant-b");
    now=Instant.parse("2026-11-05T00:00:00Z");
    DataSource ds=dataSource();
    try(Connection c=ds.getConnection();Statement s=c.createStatement()){
      s.execute("DROP TABLE IF EXISTS advertising_campaign_creative, advertising_creative, advertising_campaign, advertising_ad_placement, advertising_publisher_property, advertising_publisher_account, advertising_advertiser_account CASCADE");
    }
    apply(ds,"/db/migration/V1__advertising_accounts_and_inventory.sql");
    apply(ds,"/db/migration/V2__advertising_campaigns_and_creatives.sql");
    accountStore=new PostgresAdvertisingAccountStore(ds);
    campaignStore=new PostgresAdvertisingCampaignStore(ds);
  }

  @Test void persistsCampaignCreativeAndAssignmentWithTenantIsolation() {
    var advertiser=AdvertiserAccount.register(AdvertisingAccountId.generate(),tenantA,new PartyReference("advertiser-a"),"Advertiser",now).activate();
    accountStore.saveAdvertiser(advertiser).toCompletableFuture().join();
    var campaign=Campaign.draft(CampaignId.generate(),tenantA,advertiser.id(),"Launch",CampaignObjective.SALES,
      now,now.plusSeconds(7200),now);
    campaignStore.saveCampaign(campaign).toCompletableFuture().join();
    assertTrue(campaignStore.findCampaign(tenantA,campaign.id()).toCompletableFuture().join().isPresent());
    assertTrue(campaignStore.findCampaign(tenantB,campaign.id()).toCompletableFuture().join().isEmpty());

    var creative=Creative.draft(CreativeId.generate(),tenantA,advertiser.id(),"Launch banner",CreativeFormat.IMAGE,
      "Headline","Body",java.net.URI.create("https://advertiser.example"),"docs/tenant-a/creative-1",now).submit().approve();
    campaignStore.saveCreative(creative).toCompletableFuture().join();
    campaignStore.assignCreative(new CampaignCreativeAssignment(tenantA,campaign.id(),creative.id(),now)).toCompletableFuture().join();
    var assigned=campaignStore.findAssignedCreatives(tenantA,campaign.id()).toCompletableFuture().join();
    assertEquals(1,assigned.size());
    assertEquals(creative.id(),assigned.get(0).id());
    assertTrue(campaignStore.findAssignedCreatives(tenantB,campaign.id()).toCompletableFuture().join().isEmpty());
  }

  @Test void rejectsCrossTenantAssignmentsAndStaleCampaignUpdates() {
    var advertiser=AdvertiserAccount.register(AdvertisingAccountId.generate(),tenantA,new PartyReference("advertiser-b"),"Advertiser",now);
    accountStore.saveAdvertiser(advertiser).toCompletableFuture().join();
    var campaign=Campaign.draft(CampaignId.generate(),tenantA,advertiser.id(),"Campaign",CampaignObjective.TRAFFIC,now,null,now);
    campaignStore.saveCampaign(campaign).toCompletableFuture().join();
    var creative=Creative.draft(CreativeId.generate(),tenantA,advertiser.id(),"Text",CreativeFormat.TEXT,
      "Headline","Body",java.net.URI.create("https://advertiser.example"),null,now);
    campaignStore.saveCreative(creative).toCompletableFuture().join();
    var scheduled=campaign.schedule();
    campaignStore.saveCampaign(scheduled).toCompletableFuture().join();
    assertThrows(RuntimeException.class,()->campaignStore.saveCampaign(campaign.pause()).toCompletableFuture().join());

    var crossTenant=new CampaignCreativeAssignment(tenantB,campaign.id(),creative.id(),now);
    assertThrows(RuntimeException.class,()->campaignStore.assignCreative(crossTenant).toCompletableFuture().join());
  }

  private static void apply(DataSource ds,String resource) throws Exception {
    try(Connection c=ds.getConnection();Statement s=c.createStatement();
        InputStream in=PostgresAdvertisingCampaignStoreTest.class.getResourceAsStream(resource)){
      assertNotNull(in,"migration must be packaged: "+resource);
      String sql=new String(in.readAllBytes(),StandardCharsets.UTF_8);
      for(String command:sql.split(";"))if(!command.isBlank())s.execute(command);
    }
  }
  private static DataSource dataSource(){
    PGSimpleDataSource source=new PGSimpleDataSource();
    source.setURL(POSTGRES.getJdbcUrl());source.setUser(POSTGRES.getUsername());source.setPassword(POSTGRES.getPassword());
    return source;
  }
}
