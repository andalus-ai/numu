package tech.kayys.numou.advertising.domain;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class AdvertisingLifecycleTest {
  private final TenantId tenant = new TenantId("tenant-a");
  private final Instant now = Instant.parse("2026-11-05T00:00:00Z");

  @Test void advertiserMustNotTransitionAfterClose() {
    var account=AdvertiserAccount.register(AdvertisingAccountId.generate(),tenant,new PartyReference("party-a"),"Advertiser",now);
    var closed=account.close();
    assertEquals(AccountStatus.CLOSED,closed.status());
    assertThrows(IllegalStateException.class,closed::activate);
    assertThrows(IllegalStateException.class,closed::suspend);
  }

  @Test void publisherPropertyCanBeVerifiedAsAnExplicitTransition() {
    var property=PublisherProperty.register(PublisherPropertyId.generate(),tenant,AdvertisingAccountId.generate(),
      "Publisher site",URI.create("https://publisher.example"),now);
    assertEquals(PropertyStatus.PENDING_VERIFICATION,property.status());
    var verified=property.verify();
    assertEquals(PropertyStatus.VERIFIED,verified.status());
    assertEquals(1,verified.version());
  }

  @Test void placementCodeIsStableAndConstrained() {
    var property=PublisherPropertyId.generate();
    assertThrows(IllegalArgumentException.class,()->AdPlacement.define(
      AdPlacementId.generate(),tenant,property,"bad placement","display banner",now));
    var placement=AdPlacement.define(AdPlacementId.generate(),tenant,property,"home.hero","display-banner",now);
    assertEquals(PlacementStatus.DRAFT,placement.status());
    assertEquals(PlacementStatus.ENABLED,placement.enable().status());
  }

  @Test void propertyRejectsNonHttpUri() {
    assertThrows(IllegalArgumentException.class,()->PublisherProperty.register(PublisherPropertyId.generate(),
      tenant,AdvertisingAccountId.generate(),"Site",URI.create("ftp://publisher.example"),now));
  }
}
