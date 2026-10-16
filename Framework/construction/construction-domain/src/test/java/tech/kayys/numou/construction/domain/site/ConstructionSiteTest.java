package tech.kayys.syirkah.construction.domain.site;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ConstructionSiteTest {
    @Test
    void shouldCreateAndActivateSite() {
        var projectId = UUID.randomUUID();
        var address = new SiteAddress("Jl. Asia Afrika No. 1", "Bandung", "Jawa Barat", "40111", "Indonesia");
        var site = ConstructionSite.create(
                projectId,
                "SITE-01",
                "Main Site",
                SiteType.MAIN_SITE,
                address,
                -6.9175,
                107.6191,
                "Asia/Jakarta"
        );

        assertThat(site.status()).isEqualTo(SiteStatus.PLANNED);
        assertThat(site.siteCode()).isEqualTo("SITE-01");

        site.activate();
        assertThat(site.status()).isEqualTo(SiteStatus.ACTIVE);

        site.close();
        assertThat(site.status()).isEqualTo(SiteStatus.CLOSED);

        var events = site.pullDomainEvents();
        assertThat(events).hasSize(2); // Created + Closed
    }
}
