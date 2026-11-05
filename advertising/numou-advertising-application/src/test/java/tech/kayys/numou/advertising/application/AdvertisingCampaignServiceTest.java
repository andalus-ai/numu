package tech.kayys.numou.advertising.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.advertising.domain.*;
import tech.kayys.numou.advertising.spi.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvertisingCampaignServiceTest {
  private static final TenantId TENANT=new TenantId("tenant-a");
  private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"),ZoneOffset.UTC);

  @Test void campaignAndCreativeRequireActiveAdvertiserAndApprovedSameAdvertiserCreative() {
    Accounts accounts=new Accounts();
    Campaigns campaigns=new Campaigns();
    AdvertisingAuthorizationPort auth=(tenant,action)->CompletableFuture.completedFuture(true);
    AdvertisingCampaignService service=new AdvertisingCampaignService(accounts,campaigns,auth,CLOCK);
    AdvertisingAccountId advertiserId=AdvertisingAccountId.generate();
    AdvertiserAccount advertiser=AdvertiserAccount.register(advertiserId,TENANT,new PartyReference("party-a"),"Advertiser",CLOCK.instant());
    accounts.saveAdvertiser(advertiser).toCompletableFuture().join();
    assertThrows(CompletionException.class,()->service.createCampaign(TENANT,advertiserId,"Campaign",
      CampaignObjective.SALES,CLOCK.instant(),CLOCK.instant().plusSeconds(3600)).toCompletableFuture().join());
    accounts.saveAdvertiser(advertiser.activate()).toCompletableFuture().join();

    Campaign campaign=service.createCampaign(TENANT,advertiserId,"Campaign",CampaignObjective.SALES,
      CLOCK.instant(),CLOCK.instant().plusSeconds(3600)).toCompletableFuture().join();
    assertEquals(CampaignStatus.DRAFT,campaign.status());
    assertThrows(CompletionException.class,()->service.scheduleCampaign(TENANT,campaign.id()).toCompletableFuture().join());

    Creative creative=service.createCreative(TENANT,advertiserId,"Creative",CreativeFormat.TEXT,
      "Headline","Body",URI.create("https://advertiser.example"),null).toCompletableFuture().join();
    CreativeId unreviewedCreativeId=creative.id();
    assertThrows(CompletionException.class,()->service.assignCreative(TENANT,campaign.id(),unreviewedCreativeId).toCompletableFuture().join());
    creative=service.submitCreative(TENANT,creative.id()).toCompletableFuture().join();
    creative=service.approveCreative(TENANT,creative.id()).toCompletableFuture().join();
    assertEquals(CreativeStatus.APPROVED,creative.status());
    assertEquals(creative.id(),service.assignCreative(TENANT,campaign.id(),creative.id()).toCompletableFuture().join().creativeId());

    AdvertisingAccountId otherAdvertiserId=AdvertisingAccountId.generate();
    accounts.saveAdvertiser(AdvertiserAccount.register(otherAdvertiserId,TENANT,new PartyReference("party-b"),
      "Other advertiser",CLOCK.instant()).activate()).toCompletableFuture().join();
    Creative otherCreative=service.createCreative(TENANT,otherAdvertiserId,"Other creative",CreativeFormat.TEXT,
      "Headline","Body",URI.create("https://other-advertiser.example"),null).toCompletableFuture().join();
    otherCreative=service.submitCreative(TENANT,otherCreative.id()).toCompletableFuture().join();
    otherCreative=service.approveCreative(TENANT,otherCreative.id()).toCompletableFuture().join();
    CreativeId otherCreativeId=otherCreative.id();
    assertThrows(CompletionException.class,()->service.assignCreative(TENANT,campaign.id(),otherCreativeId).toCompletableFuture().join());

    Campaign scheduled=service.scheduleCampaign(TENANT,campaign.id()).toCompletableFuture().join();
    assertEquals(CampaignStatus.SCHEDULED,scheduled.status());
    assertEquals(CampaignStatus.ACTIVE,service.activateCampaign(TENANT,campaign.id()).toCompletableFuture().join().status());
  }

  @Test void campaignCanTargetOnlyEnabledPlacementOnVerifiedActivePublisherInventory() {
    Accounts accounts=new Accounts();
    Campaigns campaigns=new Campaigns();
    AdvertisingCampaignService service=new AdvertisingCampaignService(accounts,campaigns,
      (tenant,action)->CompletableFuture.completedFuture(true),CLOCK);
    AdvertisingAccountId advertiserId=AdvertisingAccountId.generate();
    accounts.saveAdvertiser(AdvertiserAccount.register(advertiserId,TENANT,new PartyReference("advertiser-target"),
      "Advertiser",CLOCK.instant()).activate()).toCompletableFuture().join();
    Campaign campaign=service.createCampaign(TENANT,advertiserId,"Campaign",CampaignObjective.TRAFFIC,
      CLOCK.instant(),CLOCK.instant().plusSeconds(3600)).toCompletableFuture().join();

    AdvertisingAccountId publisherId=AdvertisingAccountId.generate();
    accounts.savePublisher(PublisherAccount.register(publisherId,TENANT,new PartyReference("publisher-target"),
      "Publisher",CLOCK.instant()).activate()).toCompletableFuture().join();
    PublisherProperty property=PublisherProperty.register(PublisherPropertyId.generate(),TENANT,publisherId,
      "Publisher site",URI.create("https://publisher.example"),CLOCK.instant()).verify();
    accounts.saveProperty(property).toCompletableFuture().join();
    AdPlacement placement=AdPlacement.define(AdPlacementId.generate(),TENANT,property.id(),"top","IMAGE",CLOCK.instant()).enable();
    accounts.savePlacement(placement).toCompletableFuture().join();

    CampaignPlacementTarget target=service.targetCampaignToPlacement(TENANT,campaign.id(),placement.id()).toCompletableFuture().join();
    assertEquals(campaign.id(),target.campaignId());
    assertEquals(placement.id(),target.placementId());
  }

  @Test void campaignCannotTargetUnverifiedOrInactiveInventory() {
    Accounts accounts=new Accounts();
    Campaigns campaigns=new Campaigns();
    AdvertisingCampaignService service=new AdvertisingCampaignService(accounts,campaigns,
      (tenant,action)->CompletableFuture.completedFuture(true),CLOCK);
    AdvertisingAccountId advertiserId=AdvertisingAccountId.generate();
    accounts.saveAdvertiser(AdvertiserAccount.register(advertiserId,TENANT,new PartyReference("advertiser-target-2"),
      "Advertiser",CLOCK.instant()).activate()).toCompletableFuture().join();
    Campaign campaign=service.createCampaign(TENANT,advertiserId,"Campaign",CampaignObjective.TRAFFIC,
      CLOCK.instant(),null).toCompletableFuture().join();
    AdvertisingAccountId publisherId=AdvertisingAccountId.generate();
    accounts.savePublisher(PublisherAccount.register(publisherId,TENANT,new PartyReference("publisher-target-2"),
      "Publisher",CLOCK.instant()).activate()).toCompletableFuture().join();
    PublisherProperty property=PublisherProperty.register(PublisherPropertyId.generate(),TENANT,publisherId,
      "Pending property",URI.create("https://pending.example"),CLOCK.instant());
    accounts.saveProperty(property).toCompletableFuture().join();
    AdPlacement placement=AdPlacement.define(AdPlacementId.generate(),TENANT,property.id(),"top","IMAGE",CLOCK.instant()).enable();
    accounts.savePlacement(placement).toCompletableFuture().join();
    assertThrows(CompletionException.class,()->service.targetCampaignToPlacement(TENANT,campaign.id(),placement.id()).toCompletableFuture().join());
  }

  @Test void authorizationIsCheckedBeforeCampaignCreation() {
    Accounts accounts=new Accounts();
    Campaigns campaigns=new Campaigns();
    AdvertisingCampaignService service=new AdvertisingCampaignService(accounts,campaigns,
      (tenant,action)->CompletableFuture.completedFuture(false),CLOCK);
    assertThrows(CompletionException.class,()->service.createCampaign(TENANT,AdvertisingAccountId.generate(),"Campaign",
      CampaignObjective.TRAFFIC,CLOCK.instant(),null).toCompletableFuture().join());
    assertTrue(campaigns.campaigns.isEmpty());
  }

  private static final class Accounts implements AdvertisingAccountStore {
    final Map<String,AdvertiserAccount> advertisers=new HashMap<>();
    final Map<String,PublisherAccount> publishers=new HashMap<>();
    final Map<String,PublisherProperty> properties=new HashMap<>();
    final Map<String,AdPlacement> placements=new HashMap<>();
    private String key(TenantId t,Object id){return t.value()+":"+id;}
    public CompletionStage<AdvertiserAccount> saveAdvertiser(AdvertiserAccount a){advertisers.put(key(a.tenantId(),a.id()),a);return CompletableFuture.completedFuture(a);}
    public CompletionStage<Optional<AdvertiserAccount>> findAdvertiser(TenantId t,AdvertisingAccountId id){return CompletableFuture.completedFuture(Optional.ofNullable(advertisers.get(key(t,id))));}
    public CompletionStage<PublisherAccount> savePublisher(PublisherAccount a){publishers.put(key(a.tenantId(),a.id()),a);return CompletableFuture.completedFuture(a);}
    public CompletionStage<Optional<PublisherAccount>> findPublisher(TenantId t,AdvertisingAccountId id){return CompletableFuture.completedFuture(Optional.ofNullable(publishers.get(key(t,id))));}
    public CompletionStage<PublisherProperty> saveProperty(PublisherProperty p){properties.put(key(p.tenantId(),p.id()),p);return CompletableFuture.completedFuture(p);}
    public CompletionStage<Optional<PublisherProperty>> findProperty(TenantId t,PublisherPropertyId id){return CompletableFuture.completedFuture(Optional.ofNullable(properties.get(key(t,id))));}
    public CompletionStage<AdPlacement> savePlacement(AdPlacement p){placements.put(key(p.tenantId(),p.id()),p);return CompletableFuture.completedFuture(p);}
    public CompletionStage<Optional<AdPlacement>> findPlacement(TenantId t,AdPlacementId id){return CompletableFuture.completedFuture(Optional.ofNullable(placements.get(key(t,id))));}
  }
  private static final class Campaigns implements AdvertisingCampaignStore {
    final Map<String,Campaign> campaigns=new HashMap<>();
    final Map<String,Creative> creatives=new HashMap<>();
    final Map<String,List<CreativeId>> assignments=new HashMap<>();
    private String key(TenantId t,Object id){return t.value()+":"+id;}
    public CompletionStage<Campaign> saveCampaign(Campaign c){campaigns.put(key(c.tenantId(),c.id()),c);return CompletableFuture.completedFuture(c);}
    public CompletionStage<Optional<Campaign>> findCampaign(TenantId t,CampaignId id){return CompletableFuture.completedFuture(Optional.ofNullable(campaigns.get(key(t,id))));}
    public CompletionStage<Creative> saveCreative(Creative c){creatives.put(key(c.tenantId(),c.id()),c);return CompletableFuture.completedFuture(c);}
    public CompletionStage<Optional<Creative>> findCreative(TenantId t,CreativeId id){return CompletableFuture.completedFuture(Optional.ofNullable(creatives.get(key(t,id))));}
    public CompletionStage<CampaignCreativeAssignment> assignCreative(CampaignCreativeAssignment a){assignments.computeIfAbsent(key(a.tenantId(),a.campaignId()),k->new ArrayList<>()).add(a.creativeId());return CompletableFuture.completedFuture(a);}
    public CompletionStage<CampaignPlacementTarget> assignPlacement(CampaignPlacementTarget target){return CompletableFuture.completedFuture(target);}
    public CompletionStage<List<Creative>> findAssignedCreatives(TenantId t,CampaignId id){return CompletableFuture.completedFuture(assignments.getOrDefault(key(t,id),List.of()).stream().map(cid->creatives.get(key(t,cid))).toList());}
  }
}
