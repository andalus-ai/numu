package tech.kayys.numou.advertising.postgres;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.advertising.domain.*;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgresAdvertisingAdServingStoreTest {
  @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
  private final TenantId tenant=new TenantId("tenant-serving");
  private final Instant now=Instant.parse("2026-11-05T12:00:00Z");
  private DataSource dataSource;
  private PostgresAdvertisingAccountStore accounts;
  private PostgresAdvertisingCampaignStore campaigns;
  private PostgresAdvertisingAdServingStore serving;

  @BeforeEach void setUp() throws Exception {
    dataSource=dataSource();
    try(Connection c=dataSource.getConnection();Statement s=c.createStatement()){
      s.execute("DROP TABLE IF EXISTS advertising_ad_decision,advertising_campaign_placement,advertising_campaign_creative,advertising_creative,advertising_campaign,advertising_ad_placement,advertising_publisher_property,advertising_publisher_account,advertising_advertiser_account CASCADE");
    }
    apply("/db/migration/V1__advertising_accounts_and_inventory.sql");
    apply("/db/migration/V2__advertising_campaigns_and_creatives.sql");
    apply("/db/migration/V3__advertising_serving_and_decisions.sql");
    accounts=new PostgresAdvertisingAccountStore(dataSource);
    campaigns=new PostgresAdvertisingCampaignStore(dataSource);
    serving=new PostgresAdvertisingAdServingStore(dataSource);
  }

  @Test void servingCandidatesRequireExplicitPlacementTargetAndDecisionIsDurablyAudited() throws Exception {
    PublisherAccount publisher=PublisherAccount.register(AdvertisingAccountId.generate(),tenant,
      new PartyReference("publisher-party"),"Publisher",now).activate();
    accounts.savePublisher(publisher).toCompletableFuture().join();
    PublisherProperty property=PublisherProperty.register(PublisherPropertyId.generate(),tenant,publisher.id(),
      "Publisher site",URI.create("https://publisher.example"),now).verify();
    accounts.saveProperty(property).toCompletableFuture().join();
    AdPlacement placement=AdPlacement.define(AdPlacementId.generate(),tenant,property.id(),"article-top","IMAGE",now).enable();
    accounts.savePlacement(placement).toCompletableFuture().join();

    AdServingCandidateData targeted=createCampaign("advertiser-targeted",placement,true);
    AdServingCandidateData notTargeted=createCampaign("advertiser-not-targeted",placement,false);

    var inventory=serving.findInventory(tenant,placement.id()).toCompletableFuture().join().orElseThrow();
    assertEquals(property.id(),inventory.property().id());
    assertEquals(publisher.id(),inventory.publisher().id());

    List<AdServingCandidate> candidates=serving.findEligibleCandidates(tenant,placement.id(),"IMAGE",now).toCompletableFuture().join();
    assertEquals(1,candidates.size());
    assertEquals(targeted.creative.id(),candidates.get(0).creative().id());
    assertNotEquals(notTargeted.creative.id(),candidates.get(0).creative().id());

    AdDecision filled=AdDecision.filled(tenant,inventory,candidates.get(0),now);
    serving.recordDecision(filled).toCompletableFuture().join();
    AdDecision noFill=AdDecision.noFill(tenant,placement.id(),property.id(),AdDecisionReason.NO_ELIGIBLE_CANDIDATE,now);
    serving.recordDecision(noFill).toCompletableFuture().join();
    AdDecision unknownPlacement=AdDecision.noFill(tenant,AdPlacementId.generate(),null,AdDecisionReason.PLACEMENT_NOT_FOUND,now);
    serving.recordDecision(unknownPlacement).toCompletableFuture().join();

    try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement(
      "SELECT decision_status,decision_reason FROM advertising_ad_decision WHERE tenant_id=? ORDER BY decision_status,decision_reason")){
      s.setString(1,tenant.value());
      try(ResultSet r=s.executeQuery()){
        assertTrue(r.next());assertEquals("FILLED",r.getString(1));assertEquals("SELECTED",r.getString(2));
        assertTrue(r.next());assertEquals("NO_FILL",r.getString(1));assertEquals("NO_ELIGIBLE_CANDIDATE",r.getString(2));
        assertTrue(r.next());assertEquals("NO_FILL",r.getString(1));assertEquals("PLACEMENT_NOT_FOUND",r.getString(2));
        assertFalse(r.next());
      }
    }
  }

  private AdServingCandidateData createCampaign(String party,AdPlacement placement,boolean target) {
    AdvertiserAccount advertiser=AdvertiserAccount.register(AdvertisingAccountId.generate(),tenant,
      new PartyReference(party),party,now).activate();
    accounts.saveAdvertiser(advertiser).toCompletableFuture().join();
    Campaign campaign=Campaign.draft(CampaignId.generate(),tenant,advertiser.id(),party,
      CampaignObjective.TRAFFIC,now.minusSeconds(300),now.plusSeconds(3600),now).schedule().activate(now);
    campaigns.saveCampaign(campaign).toCompletableFuture().join();
    Creative creative=Creative.draft(CreativeId.generate(),tenant,advertiser.id(),party,CreativeFormat.IMAGE,
      "Headline","Body",URI.create("https://advertiser.example"),"document-"+party,now).submit().approve();
    campaigns.saveCreative(creative).toCompletableFuture().join();
    campaigns.assignCreative(new CampaignCreativeAssignment(tenant,advertiser.id(),campaign.id(),creative.id(),now))
      .toCompletableFuture().join();
    if(target)campaigns.assignPlacement(new CampaignPlacementTarget(tenant,advertiser.id(),campaign.id(),placement.id(),now))
      .toCompletableFuture().join();
    return new AdServingCandidateData(campaign,creative);
  }

  private record AdServingCandidateData(Campaign campaign,Creative creative){}

  private void apply(String resource) throws Exception {
    try(Connection c=dataSource.getConnection();Statement s=c.createStatement();
        InputStream in=getClass().getResourceAsStream(resource)){
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
