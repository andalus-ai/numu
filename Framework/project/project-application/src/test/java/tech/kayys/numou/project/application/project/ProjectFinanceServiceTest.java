package tech.kayys.syirkah.project.application.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationErrorException;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.application.support.InMemoryProjectFinanceStore;
import tech.kayys.syirkah.project.application.support.InMemoryProjectRepository;
import tech.kayys.syirkah.project.domain.project.InvalidProjectStateException;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectNumber;
import tech.kayys.syirkah.project.domain.project.ProjectType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Project finance service")
class ProjectFinanceServiceTest {

    private static final UUID CUSTOMER_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final DateRange PERIOD = new DateRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
    );

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryProjectFinanceStore store = new InMemoryProjectFinanceStore();

    private final ProjectFinanceService service =
            new ProjectFinanceService(projects, store);

    private Project saveProject(String number, boolean active) {
        var project = Project.create(
                ProjectId.generate(),
                ProjectNumber.of(number),
                "E-Commerce Replatform",
                ProjectType.CLIENT_BILLABLE,
                CUSTOMER_ID
        );

        if (active) {
            project.plan(PERIOD);
            project.start();
        }

        project.pullDomainEvents();
        projects.save(project).toCompletableFuture().join();

        return project;
    }

    @Test
    @DisplayName("profitability is revenue minus cost, with margin percentage")
    void computesProfitability() {
        var project = saveProject("PRJ-501", true);

        service.recordCost(project.id(), "LABOR", new BigDecimal("20000"), "USD", "TS-01")
                .await().indefinitely();
        service.recordCost(project.id(), "CLOUD_INFRA", new BigDecimal("5000"), "USD", "INV-99")
                .await().indefinitely();
        service.recordRevenue(project.id(), "MILESTONE_1", new BigDecimal("40000"), "USD", "INV-CUST-1")
                .await().indefinitely();

        var profitability = service.profitability(project.id()).await().indefinitely();

        assertEquals(new BigDecimal("40000"), profitability.totalRevenue());
        assertEquals(new BigDecimal("25000"), profitability.totalCost());
        assertEquals(new BigDecimal("15000"), profitability.grossMargin());
        assertEquals(new BigDecimal("37.5000"), profitability.marginPercentage());
    }

    @Test
    @DisplayName("entries are accumulated per project")
    void entriesAreScopedPerProject() {
        var first = saveProject("PRJ-502", true);
        var second = saveProject("PRJ-503", true);

        service.recordCost(first.id(), "LABOR", new BigDecimal("1000"), "USD", "TS-1")
                .await().indefinitely();
        service.recordCost(second.id(), "LABOR", new BigDecimal("2500"), "USD", "TS-2")
                .await().indefinitely();

        assertEquals(
                1,
                service.costs(first.id()).await().indefinitely().size()
        );
        assertEquals(
                new BigDecimal("2500"),
                service.profitability(second.id())
                        .await().indefinitely()
                        .totalCost()
        );
    }

    @Test
    @DisplayName("revenue requires an ACTIVE project")
    void revenueRequiresActiveProject() {
        var draft = saveProject("PRJ-504", false);

        assertThrows(
                InvalidProjectStateException.class,
                () -> service.recordRevenue(
                        draft.id(),
                        "MILESTONE_1",
                        new BigDecimal("1000"),
                        "USD",
                        "INV-CUST-2"
                ).await().indefinitely()
        );
    }

    @Test
    @DisplayName("a completed project cannot receive further cost entries")
    void completedProjectRejectsCosts() {
        var project = saveProject("PRJ-505", true);
        var active = projects.findById(project.id())
                .toCompletableFuture().join().orElseThrow();

        active.complete();
        projects.save(active).toCompletableFuture().join();

        assertThrows(
                InvalidProjectStateException.class,
                () -> service.recordCost(
                        project.id(),
                        "LABOR",
                        new BigDecimal("1000"),
                        "USD",
                        "TS-9"
                ).await().indefinitely()
        );
    }

    @Test
    @DisplayName("an unknown project yields a typed PROJECT_NOT_FOUND failure")
    void unknownProjectFails() {
        var exception = assertThrows(
                ApplicationErrorException.class,
                () -> service.profitability(ProjectId.generate())
                        .await().indefinitely()
        );

        assertEquals("PROJECT_NOT_FOUND", exception.error().code());
    }

    @Test
    @DisplayName("cost entries must carry a positive amount and a category")
    void costEntriesAreValidated() {
        var project = saveProject("PRJ-506", true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordCost(
                        project.id(),
                        "LABOR",
                        BigDecimal.ZERO,
                        "USD",
                        "TS-1"
                ).await().indefinitely()
        );
    }
}
