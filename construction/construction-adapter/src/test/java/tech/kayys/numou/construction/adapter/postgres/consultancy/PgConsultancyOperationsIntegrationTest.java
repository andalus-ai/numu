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
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverable;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverableId;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverableStatus;
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
    void deliverableRoundTripsReviewStateAndRemainsTenantScoped() {
        String tenant = "consultancy-deliverable-" + UUID.randomUUID();
        String number = "ENG-" + UUID.randomUUID();
        ConsultancyEngagement engagement = newEngagement(number);
        UUID client = engagement.clientOrganizationId();
        UUID consultant = engagement.consultantOrganizationId();
        ConsultancyDeliverable rejected = ConsultancyDeliverable.create(
                ConsultancyEngagementId.of(engagement.id().value()), client, consultant,
                "D-01", "Structural report", "Structural calculations and signed report");
        rejected.submit();
        rejected.reject(client, "Missing calculation appendix");

        ConsultancyDeliverable saved = runner.inTransaction(context ->
                context.consultancy().saveEngagement(tenant, engagement)
                        .flatMap(ignored -> context.consultancy().saveDeliverable(tenant, rejected)))
                .await().indefinitely();

        assertEquals(rejected.id(), saved.id());
        assertEquals(engagement.id().value(), saved.engagementId().value());
        assertEquals(2, saved.revision() - 1 + 1);
        assertEquals(ConsultancyDeliverableStatus.REJECTED, saved.status());
        assertEquals(client, saved.reviewedByOrganizationId());
        assertEquals("Missing calculation appendix", saved.reviewComment());

        var sameTenant = runner.inTransaction(context -> context.consultancy()
                .findDeliverable(tenant, ConsultancyDeliverableId.of(saved.id().value())))
                .await().indefinitely();
        var otherTenant = runner.inTransaction(context -> context.consultancy()
                .findDeliverable(tenant + "-other", ConsultancyDeliverableId.of(saved.id().value())))
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


    @Test
    void lifecycleUpdatesPersistValidTransitionsAndProposalRevisions() {
        String tenant = "consultancy-lifecycle-" + UUID.randomUUID();
        ConsultancyEngagement engagement = runner.inTransaction(context ->
                context.consultancy().saveEngagement(tenant, newEngagement("ENG-" + UUID.randomUUID())))
                .await().indefinitely();

        ConsultancyEngagement proposed = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant, engagement.id())
                        .map(found -> found.orElseThrow())
                        .invoke(ConsultancyEngagement::submitProposal)
                        .flatMap(updated -> context.consultancy().updateEngagement(tenant, updated)))
                .await().indefinitely();
        assertEquals("PROPOSED", proposed.status().name());

        ConsultancyEngagement revised = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant, engagement.id())
                        .map(found -> found.orElseThrow())
                        .invoke(current -> current.reviseProposal("Revised title", "Revised scope"))
                        .flatMap(updated -> context.consultancy().updateEngagement(tenant, updated)))
                .await().indefinitely();
        assertEquals("DRAFT", revised.status().name());
        assertEquals(2, revised.scopeRevision());
        assertEquals("Revised scope", revised.scopeStatement());

        ConsultancyEngagement reProposed = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant, engagement.id())
                        .map(found -> found.orElseThrow())
                        .invoke(ConsultancyEngagement::submitProposal)
                        .flatMap(updated -> context.consultancy().updateEngagement(tenant, updated)))
                .await().indefinitely();
        ConsultancyEngagement active = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant, engagement.id())
                        .map(found -> found.orElseThrow())
                        .invoke(ConsultancyEngagement::activate)
                        .flatMap(updated -> context.consultancy().updateEngagement(tenant, updated)))
                .await().indefinitely();
        ConsultancyEngagement completed = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant, engagement.id())
                        .map(found -> found.orElseThrow())
                        .invoke(ConsultancyEngagement::complete)
                        .flatMap(updated -> context.consultancy().updateEngagement(tenant, updated)))
                .await().indefinitely();

        assertEquals("PROPOSED", reProposed.status().name());
        assertEquals("ACTIVE", active.status().name());
        assertEquals("COMPLETED", completed.status().name());
        assertEquals(2, completed.scopeRevision());
    }

    @Test
    void lifecycleUpdatesRejectStaleConcurrentStateAndPreserveTenantIsolation() {
        String tenant = "consultancy-concurrency-" + UUID.randomUUID();
        ConsultancyEngagement saved = runner.inTransaction(context ->
                context.consultancy().saveEngagement(tenant, newEngagement("ENG-" + UUID.randomUUID())))
                .await().indefinitely();
        runner.inTransaction(context -> context.consultancy().findEngagement(tenant, saved.id())
                .map(found -> found.orElseThrow())
                .invoke(ConsultancyEngagement::submitProposal)
                .flatMap(updated -> context.consultancy().updateEngagement(tenant, updated)))
                .await().indefinitely();

        ConsultancyEngagement staleCopy = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant, saved.id()).map(found -> found.orElseThrow()))
                .await().indefinitely();
        runner.inTransaction(context -> context.consultancy().findEngagement(tenant, saved.id())
                .map(found -> found.orElseThrow())
                .invoke(ConsultancyEngagement::activate)
                .flatMap(updated -> context.consultancy().updateEngagement(tenant, updated)))
                .await().indefinitely();

        staleCopy.reviseProposal("Stale title", "Stale scope");
        assertThrows(DefaultPgConsultancyOperationsFactory.ConcurrentConsultancyEngagementUpdateException.class,
                () -> runner.inTransaction(context -> context.consultancy().updateEngagement(tenant, staleCopy))
                        .await().indefinitely());

        ConsultancyEngagement wrongTenant = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant, saved.id()).map(found -> found.orElseThrow()))
                .await().indefinitely();
        wrongTenant.complete();
        assertThrows(DefaultPgConsultancyOperationsFactory.ConcurrentConsultancyEngagementUpdateException.class,
                () -> runner.inTransaction(context -> context.consultancy()
                        .updateEngagement(tenant + "-other", wrongTenant)).await().indefinitely());
    }

    @Test
    void cancellationPersistsRequiredReason() {
        String tenant = "consultancy-cancel-" + UUID.randomUUID();
        ConsultancyEngagement saved = runner.inTransaction(context ->
                context.consultancy().saveEngagement(tenant, newEngagement("ENG-" + UUID.randomUUID())))
                .await().indefinitely();
        ConsultancyEngagement cancelled = runner.inTransaction(context ->
                context.consultancy().findEngagement(tenant, saved.id())
                        .map(found -> found.orElseThrow())
                        .invoke(engagement -> engagement.cancel("Client terminated the engagement"))
                        .flatMap(updated -> context.consultancy().updateEngagement(tenant, updated)))
                .await().indefinitely();

        assertEquals("CANCELLED", cancelled.status().name());
        assertEquals("Client terminated the engagement", cancelled.cancellationReason());
    }

    private static ConsultancyEngagement newEngagement(String number) {
        return ConsultancyEngagement.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                number, "Engineering consultancy", "Design review and construction supervision");
    }
}
