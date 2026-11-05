package tech.kayys.numou.advertising.domain;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CampaignCreativeLifecycleTest {
  private static final TenantId TENANT=new TenantId("tenant-a");
  private static final Instant NOW=Instant.parse("2026-11-05T00:00:00Z");

  @Test void campaignTransitionsRespectWindowAndTerminalStates() {
    Campaign campaign=Campaign.draft(CampaignId.generate(),TENANT,AdvertisingAccountId.generate(),
      "Holiday campaign",CampaignObjective.SALES,NOW,NOW.plusSeconds(3600),NOW);
    campaign=campaign.schedule();
    assertEquals(CampaignStatus.SCHEDULED,campaign.status());
    Campaign scheduled=campaign;
    assertThrows(IllegalStateException.class,()->scheduled.activate(NOW.minusSeconds(1)));
    campaign=campaign.activate(NOW);
    assertEquals(CampaignStatus.ACTIVE,campaign.status());
    campaign=campaign.pause();
    assertEquals(CampaignStatus.PAUSED,campaign.status());
    campaign=campaign.complete(NOW.plusSeconds(3600));
    assertEquals(CampaignStatus.COMPLETED,campaign.status());
    Campaign completed=campaign;
    assertThrows(IllegalStateException.class,completed::archive);
  }

  @Test void nonTextCreativeRequiresDocumentAndReviewBeforeAssignment() {
    assertThrows(IllegalArgumentException.class,()->Creative.draft(CreativeId.generate(),TENANT,
      AdvertisingAccountId.generate(),"Banner",CreativeFormat.IMAGE,"Headline",null,
      URI.create("https://advertiser.example"),null,NOW));
    Creative draft=Creative.draft(CreativeId.generate(),TENANT,AdvertisingAccountId.generate(),
      "Banner",CreativeFormat.IMAGE,"Headline","Description",URI.create("https://advertiser.example"),
      "documents/tenant-a/creative-1",NOW);
    Creative submitted=draft.submit();
    assertEquals(CreativeStatus.SUBMITTED,submitted.status());
    assertEquals(CreativeStatus.APPROVED,submitted.approve().status());
    assertThrows(IllegalStateException.class,draft::approve);
    assertThrows(IllegalArgumentException.class,()->draft.reject(" "));
  }

  @Test void creativeRequiresDestinationAndContentForSubmission() {
    Creative creative=Creative.draft(CreativeId.generate(),TENANT,AdvertisingAccountId.generate(),
      "Text ad",CreativeFormat.TEXT,null,null,null,null,NOW);
    assertThrows(IllegalStateException.class,creative::submit);
  }
}
