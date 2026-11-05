package tech.kayys.numou.advertising.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.advertising.domain.*;
import tech.kayys.numou.advertising.spi.AdvertisingAdServingStore;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvertisingAdServingServiceTest {
  private static final Instant NOW=Instant.parse("2026-11-05T12:00:00Z");
  private static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
  private static final TenantId TENANT=new TenantId("tenant-serving");

  @Test void selectsOnlyEligibleCampaignCreativeForExplicitlyVerifiedInventoryAndRecordsDecision() {
    Fixture f=new Fixture();
    AdServingCandidate newer=f.candidate(NOW.minusSeconds(60),"newer");
    AdServingCandidate older=f.candidate(NOW.minusSeconds(120),"older");
    f.store.candidates=List.of(newer,older);
    var result=f.service.serve(f.request("https://publisher.example/article","IMAGE")).toCompletableFuture().join();

    assertEquals(AdDecisionStatus.FILLED,result.decision().status());
    assertEquals(AdDecisionReason.SELECTED,result.decision().reason());
    assertEquals(older.creative().id(),result.creative().orElseThrow().id());
    assertEquals(1,f.store.decisions.size());
    assertEquals(result.decision(),f.store.decisions.get(0));
  }

  @Test void returnsAuditedNoFillForOriginFormatAndDisabledInventory() {
    Fixture origin=new Fixture();
    var originResult=origin.service.serve(origin.request("https://attacker.example/fake","IMAGE")).toCompletableFuture().join();
    assertEquals(AdDecisionReason.PROPERTY_ORIGIN_MISMATCH,originResult.decision().reason());
    assertTrue(originResult.creative().isEmpty());

    Fixture format=new Fixture();
    var formatResult=format.service.serve(format.request("https://publisher.example/page","VIDEO")).toCompletableFuture().join();
    assertEquals(AdDecisionReason.FORMAT_MISMATCH,formatResult.decision().reason());

    Fixture disabled=new Fixture();
    disabled.inventory=new AdServingInventory(disabled.placement.disable(),disabled.property,disabled.publisher);
    var disabledResult=disabled.service.serve(disabled.request("https://publisher.example/page","IMAGE")).toCompletableFuture().join();
    assertEquals(AdDecisionReason.INVENTORY_NOT_ELIGIBLE,disabledResult.decision().reason());
    assertEquals(3,origin.store.decisions.size()+format.store.decisions.size()+disabled.store.decisions.size());
  }

  @Test void noEligibleCandidatesProducesNoFillAndExpiredOrUnapprovedCandidatesAreFiltered() {
    Fixture f=new Fixture();
    AdServingCandidate expired=f.candidate(NOW.minusSeconds(100),"expired");
    Campaign expiredCampaign=new Campaign(expired.campaign().id(),TENANT,expired.campaign().advertiserId(),
      expired.campaign().name(),expired.campaign().objective(),NOW.minusSeconds(1000),NOW.minusSeconds(1),
      CampaignStatus.ACTIVE,expired.campaign().createdAt(),expired.campaign().version());
    f.store.candidates=List.of(new AdServingCandidate(expiredCampaign,expired.advertiser(),expired.creative()));
    var result=f.service.serve(f.request("https://publisher.example/page","IMAGE")).toCompletableFuture().join();
    assertEquals(AdDecisionStatus.NO_FILL,result.decision().status());
    assertEquals(AdDecisionReason.NO_ELIGIBLE_CANDIDATE,result.decision().reason());
    assertTrue(result.creative().isEmpty());
  }

  @Test void missingPlacementIsAnAuditedNoFill() {
    Fixture f=new Fixture();
    f.store.inventory=Optional.empty();
    var result=f.service.serve(f.request("https://publisher.example/page","IMAGE")).toCompletableFuture().join();
    assertEquals(AdDecisionReason.PLACEMENT_NOT_FOUND,result.decision().reason());
    assertNull(result.decision().propertyId());
  }

  @Test void rejectsMalformedServingRequest() {
    assertThrows(IllegalArgumentException.class,()->new AdServingRequest(TENANT,AdPlacementId.generate(),
      URI.create("javascript:alert(1)"),"IMAGE"));
    assertThrows(IllegalArgumentException.class,()->new AdServingRequest(TENANT,AdPlacementId.generate(),
      URI.create("https://publisher.example"),"bad format"));
    assertThrows(IllegalArgumentException.class,()->new AdServingRequest(TENANT,AdPlacementId.generate(),
      URI.create("https://trusted@publisher.example/page"),"IMAGE"));
  }

  private static final class Fixture {
    final AdPlacement placement=AdPlacement.define(AdPlacementId.generate(),TENANT,
      PublisherPropertyId.generate(),"article-top","IMAGE",NOW).enable();
    final PublisherProperty property=PublisherProperty.register(placement.propertyId(),TENANT,
      AdvertisingAccountId.generate(),"Publisher site",URI.create("https://publisher.example"),NOW).verify();
    final PublisherAccount publisher=PublisherAccount.register(property.publisherId(),TENANT,
      new PartyReference("publisher-party"),"Publisher",NOW).activate();
    AdServingInventory inventory=new AdServingInventory(placement,property,publisher);
    final Store store=new Store();
    final AdvertisingAdServingService service=new AdvertisingAdServingService(store,CLOCK);
    Fixture(){store.inventory=Optional.of(inventory);}
    AdServingRequest request(String page,String format){return new AdServingRequest(TENANT,placement.id(),URI.create(page),format);}
    AdServingCandidate candidate(Instant created,String label) {
      AdvertisingAccountId advertiserId=AdvertisingAccountId.generate();
      AdvertiserAccount advertiser=AdvertiserAccount.register(advertiserId,TENANT,
        new PartyReference("advertiser-"+label),"Advertiser "+label,created).activate();
      Campaign campaign=Campaign.draft(CampaignId.generate(),TENANT,advertiserId,"Campaign "+label,
        CampaignObjective.TRAFFIC,NOW.minusSeconds(300),NOW.plusSeconds(300),created).schedule().activate(NOW);
      Creative creative=Creative.draft(CreativeId.generate(),TENANT,advertiserId,"Creative "+label,
        CreativeFormat.IMAGE,"Headline","Body",URI.create("https://advertiser.example"),"document-"+label,created)
        .submit().approve();
      return new AdServingCandidate(campaign,advertiser,creative);
    }
  }

  private static final class Store implements AdvertisingAdServingStore {
    Optional<AdServingInventory> inventory;
    List<AdServingCandidate> candidates=List.of();
    final List<AdDecision> decisions=new ArrayList<>();
    Store(){ }
    public CompletionStage<Optional<AdServingInventory>> findInventory(TenantId tenant,AdPlacementId id){
      return CompletableFuture.completedFuture(inventory);
    }
    public CompletionStage<List<AdServingCandidate>> findEligibleCandidates(TenantId tenant,AdPlacementId id,String format,Instant at){
      return CompletableFuture.completedFuture(candidates);
    }
    public CompletionStage<AdDecision> recordDecision(AdDecision decision){
      decisions.add(decision);return CompletableFuture.completedFuture(decision);
    }
  }
}
