package tech.kayys.numou.construction.adapter.postgres.consultancy;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.construction.adapter.postgres.idempotency.PgTransactionalCommandRunner;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagement;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagementId;
import tech.kayys.numou.construction.domain.boq.BoqCalculationPolicy;
import tech.kayys.numou.construction.domain.boq.BoqRevisionSnapshot;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL integration coverage; requires the construction migration bundle. */
@QuarkusTest
class PgConsultancyOperationsIntegrationTest {
    @Inject PgPool pool;
    private PgTransactionalCommandRunner runner;

    @BeforeEach
    void createRunner() {
        runner = new PgTransactionalCommandRunner(pool, connection -> new TransactionalCommandContext.BoqRevisionOperations() {
            @Override public Uni<BoqRevisionSnapshot> captureDraft(String tenantId, UUID boqId, UUID actorId, BoqCalculationPolicy policy, Instant now) {
                return Uni.createFrom().failure(new UnsupportedOperationException("not used by this test"));
            }
            @Override public Uni<BoqRevisionSnapshot> submitForReview(String tenantId, UUID boqId, int revisionNumber, UUID actorId, Instant now) {
                return Uni.createFrom().failure(new UnsupportedOperationException("not used by this test"));
            }
            @Override public Uni<BoqRevisionSnapshot> approve(String tenantId, UUID boqId, int revisionNumber, UUID actorId, Instant now) {
                return Uni.createFrom().failure(new UnsupportedOperationException("not used by this test"));
            }
        }, new DefaultPgConsultancyOperationsFactory());
    }

    @Test
    void insertRoundTripsAndLookupIsTenantScoped() {
        String tenant = "consultancy-it-" + UUID.randomUUID();
        ConsultancyEngagement engagement = newEngagement("ENG-" + UUID.randomUUID());

        ConsultancyEngagement saved = runner.inTransaction(context ->
                context.consultancy().saveEngagement(tenant, engagement)).await().indefinitely();
        assertEquals(engagement.id(), saved.id());
        assertEquals(engagement.projectId(), saved.projectId());
        assertEquals(engagement.title(), saved.title());
        assertEquals(engagement.scopeStatement(), saved.scopeStatement());
        assertEquals(engagement.status(), saved.status());
        assertEquals(1, saved.scopeRevision());

        var sameTenant = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant, ConsultancyEngagementId.of(saved.id().value())))
                .await().indefinitely();
        var otherTenant = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant + "-other", ConsultancyEngagementId.of(saved.id().value())))
                .await().indefinitely();
        assertTrue(sameTenant.isPresent());
        assertTrue(otherTenant.isEmpty());
    }

    @Test
    void tenantScopedEngagementNumberUniquenessIsEnforcedByDatabase() {
        String tenant = "consultancy-unique-" + UUID.randomUUID();
        String number = "ENG-" + UUID.randomUUID();
        runner.inTransaction(context -> context.consultancy().saveEngagement(tenant, newEngagement(number)))
                .await().indefinitely();

        assertThrows(RuntimeException.class, () -> runner.inTransaction(context ->
                context.consultancy().saveEngagement(tenant, newEngagement(number))).await().indefinitely());
        // The same business number is valid in another tenant.
        ConsultancyEngagement otherTenant = runner.inTransaction(context ->
                context.consultancy().saveEngagement(tenant + "-other", newEngagement(number)))
                .await().indefinitely();
        assertEquals(number, otherTenant.engagementNumber());
    }

    @Test
    void failedCommandRollsBackEngagementInsert() {
        String tenant = "consultancy-rollback-" + UUID.randomUUID();
        ConsultancyEngagement engagement = newEngagement("ENG-" + UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> runner.inTransaction(context ->
                context.consultancy().saveEngagement(tenant, engagement)
                        .flatMap(saved -> Uni.createFrom().failure(new IllegalStateException("injected rollback"))))
                .await().indefinitely());

        Row row = pool.preparedQuery("SELECT count(*) AS n FROM construction_consultancy_engagement WHERE tenant_id=$1 AND engagement_id=$2")
                .execute(Tuple.of(tenant, engagement.id().value())).await().indefinitely().iterator().next();
        assertEquals(0L, row.getLong("n"));
    }

    private static ConsultancyEngagement newEngagement(String number) {
        return ConsultancyEngagement.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                number, "Engineering consultancy", "Design review and construction supervision");
    }
}
