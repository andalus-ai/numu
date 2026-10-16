package tech.kayys.syirkah.construction.adapter.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.construction.domain.site.*;

import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class InMemoryConstructionSiteRepositoryTest {
    private InMemoryConstructionSiteRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryConstructionSiteRepository();
    }

    @Test
    void shouldSaveAndFindSite() {
        var projectId = UUID.randomUUID();
        var address = new SiteAddress("Jl. Sudirman 12", "Jakarta", "DKI", "10220", "ID");
        var site = ConstructionSite.create(projectId, "SITE-JKT", "Jakarta Tower", SiteType.MAIN_SITE, address, -6.2, 106.8, "Asia/Jakarta");

        repository.save(site).toCompletableFuture().join();

        var sites = repository.findByProjectId(projectId).toCompletableFuture().join();
        assertThat(sites).hasSize(1);
        assertThat(sites.get(0).siteCode()).isEqualTo("SITE-JKT");
    }
}
