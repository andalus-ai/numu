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

    Creative creative=service.createCreative(TENANT,advertiserId,"Creative",CreativeFormat.TEXT,
      "Headline","Body",URI.create("https://advertiser.example"),null).toCompletableFuture().join();
    assertThrows(CompletionException.class,()->service.assignCreative(TENANT,campaign.id(),creative.id()).toCompletableFuture().join());
    creative=service.submitCreative(TENANT,creative.id()).toCompletableFuture().join();
    creative=service.approveCreative(TENANT,creative.id()).toCompletableFuture().join();
    assertEquals(CreativeStatus.APPROVED,creative.status());
    assertEquals(creative.id(),service.assignCreative(TENANT,campaign.id(),creative.id()).toCompletableFuture().join().creativeId());

    Campaign scheduled=service.scheduleCampaign(TENANT,campaign.id()).toCompletableFuture().join();
    assertEquals(CampaignStatus.SCHEDULED,scheduled.status());
    assertEquals(CampaignStatus.ACTIVE,service.activateCampaign(TENANT,campaign.id()).toCompletableFuture().join().status());
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
    private String key(TenantId t,Object id){return t.value()+":"+id;}
    public CompletionStage<AdvertiserAccount> saveAdvertiser(AdvertiserAccount a){advertisers.put(key(a.tenantId(),a.id()),a);return CompletableFuture.completedFuture(a);}
    public CompletionStage<Optional<AdvertiserAccount>> findAdvertiser(TenantId t,AdvertisingAccountId id){return CompletableFuture.completedFuture(Optional.ofNullable(advertisers.get(key(t,id))));}
    public CompletionStage<PublisherAccount> savePublisher(PublisherAccount a){return CompletableFuture.completedFuture(a);}
    public CompletionStage<Optional<PublisherAccount>> findPublisher(TenantId t,AdvertisingAccountId id){return CompletableFuture.completedFuture(Optional.empty());}
    public CompletionStage<PublisherProperty> saveProperty(PublisherProperty p){return CompletableFuture.completedFuture(p);}
    public CompletionStage<Optional<PublisherProperty>> findProperty(TenantId t,PublisherPropertyId id){return CompletableFuture.completedFuture(Optional.empty());}
    public CompletionStage<AdPlacement> savePlacement(AdPlacement p){return CompletableFuture.completedFuture(p);}
    public CompletionStage<Optional<AdPlacement>> findPlacement(TenantId t,AdPlacementId id){return CompletableFuture.completedFuture(Optional.empty());}
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
    public CompletionStage<List<Creative>> findAssignedCreatives(TenantId t,CampaignId id){return CompletableFuture.completedFuture(assignments.getOrDefault(key(t,id),List.of()).stream().map(cid->creatives.get(key(t,cid))).toList());}
  }
}
