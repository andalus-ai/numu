package tech.kayys.numou.mobility.api;

import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.wallet.application.WalletService;
import tech.kayys.numou.wallet.application.WalletView;
import tech.kayys.numou.wallet.domain.OwnerKind;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the production WalletService against the real PostgreSQL repositories and transaction boundary.
 * Run with -Ppg-integration against a dedicated database with the Mobility and framework migrations applied.
 *
 * <p>All test operations execute asynchronously on a Vert.x context, as required by Hibernate Reactive Panache.
 */
@io.quarkus.test.junit.QuarkusTest
class WalletPostgresIntegrationTest {
    @Inject WalletService wallets;
    @Inject UnitOfWork unitOfWork;
    @Inject PgPool pool;

    @Test
    @RunOnVertxContext
    void persistedReplayDoesNotApplyTheCreditTwice(UniAsserter asserter) {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-replay-" + UUID.randomUUID();

        asserter.execute(() -> wallets.credit(tenant, OwnerKind.RIDER, owner,
                new BigDecimal("1200"), ref, "integration").replaceWithVoid());
        asserter.assertThat(() -> wallets.credit(tenant, OwnerKind.RIDER, owner,
                new BigDecimal("1200"), ref, "integration"), replay -> assertAmount("1200", replay.balance()));
        asserter.assertEquals(() -> ledgerCount(tenant, ref), 1L);
        asserter.assertThat(() -> outboxCount(tenant), count ->
                assertTrue(count >= 1L, "the credit should emit an outbox event"));
        asserter.assertEquals(() -> wallets.history(tenant, OwnerKind.RIDER, owner, 20)
                .map(entries -> entries.stream().filter(entry -> ref.equals(entry.ref())).count()), 1L);
    }

    @Test
    @RunOnVertxContext
    void concurrentRequestsWithTheSameReferenceProduceOneMovement(UniAsserter asserter) {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-race-" + UUID.randomUUID();

        asserter.execute(() -> Uni.combine().all().unis(
                wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("2500"), ref, "integration"),
                wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("2500"), ref, "integration"))
                .asTuple().replaceWithVoid());
        asserter.assertThat(() -> wallets.view(tenant, OwnerKind.RIDER, owner),
                view -> assertAmount("2500", view.balance()));
        asserter.assertEquals(() -> ledgerCount(tenant, ref), 1L);
    }

    @Test
    @RunOnVertxContext
    void reusingReferenceWithDifferentAmountIsRejectedWithoutChangingTheLedger(UniAsserter asserter) {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-conflict-" + UUID.randomUUID();

        asserter.execute(() -> wallets.credit(tenant, OwnerKind.RIDER, owner,
                new BigDecimal("1000"), ref, "integration").replaceWithVoid());
        asserter.assertEquals(() -> wallets.credit(tenant, OwnerKind.RIDER, owner,
                new BigDecimal("2000"), ref, "integration")
                .onItem().transformToUni(ignored -> Uni.createFrom().item(false))
                .onFailure(BusinessRuleViolation.class).recoverWithItem(true), true);
        asserter.assertThat(() -> wallets.view(tenant, OwnerKind.RIDER, owner),
                view -> assertAmount("1000", view.balance()));
        asserter.assertEquals(() -> ledgerCount(tenant, ref), 1L);
    }

    @Test
    @RunOnVertxContext
    void outerUnitOfWorkFailureRollsBackWalletAndLedgerTogether(UniAsserter asserter) {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-rollback-" + UUID.randomUUID();

        asserter.assertEquals(() -> unitOfWork.execute(() ->
                wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("900"), ref, "integration")
                        .flatMap(ignored -> Uni.createFrom().<WalletView>failure(
                                new IllegalStateException("injected wallet transaction failure"))))
                .onItem().transformToUni(ignored -> Uni.createFrom().item(false))
                .onFailure(IllegalStateException.class).recoverWithItem(failure -> {
                    assertTrue(failure.getMessage().contains("injected wallet transaction failure"));
                    return true;
                }), true);
        asserter.assertThat(() -> wallets.view(tenant, OwnerKind.RIDER, owner),
                view -> assertAmount("0", view.balance()));
        asserter.assertEquals(() -> ledgerCount(tenant, ref), 0L);
        asserter.assertEquals(() -> outboxCount(tenant), 0L);
    }

    @Test
    @RunOnVertxContext
    void walletAndLedgerReadsRemainTenantScoped(UniAsserter asserter) {
        UUID owner = UUID.randomUUID();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String ref = "it-tenant-" + UUID.randomUUID();

        asserter.execute(() -> wallets.credit(tenantA, OwnerKind.RIDER, owner,
                new BigDecimal("700"), ref, "tenant A").replaceWithVoid());
        asserter.assertThat(() -> wallets.view(tenantB, OwnerKind.RIDER, owner),
                view -> assertAmount("0", view.balance()));
        asserter.assertEquals(() -> ledgerCount(tenantB, ref), 0L);
        asserter.assertEquals(() -> ledgerCount(tenantA, ref), 1L);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected monetary amount " + expected + " but was " + actual);
    }

    private Uni<Long> ledgerCount(UUID tenantId, String ref) {
        return pool.withTransaction(connection -> setTenant(connection, tenantId)
                .flatMap(ignored -> connection.preparedQuery(
                                "SELECT count(*) AS n FROM wallet_ledger WHERE tenant_id=$1 AND ref=$2")
                        .execute(Tuple.of(tenantId, ref))
                        .map(rows -> rows.iterator().next().getLong("n"))));
    }

    private Uni<Long> outboxCount(UUID tenantId) {
        return pool.withTransaction(connection -> setTenant(connection, tenantId)
                .flatMap(ignored -> connection.preparedQuery(
                                "SELECT count(*) AS n FROM platform_outbox WHERE tenant_id=$1")
                        .execute(Tuple.of(tenantId))
                        .map(rows -> rows.iterator().next().getLong("n"))));
    }

    private static Uni<Void> setTenant(SqlConnection connection, UUID tenantId) {
        return connection.preparedQuery("SELECT set_config('app.tenant_id', $1, true)")
                .execute(Tuple.of(tenantId.toString())).replaceWithVoid();
    }
}
