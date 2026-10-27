package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.wallet.application.WalletService;
import tech.kayys.numou.mobility.wallet.application.WalletView;
import tech.kayys.numou.mobility.wallet.domain.OwnerKind;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the production WalletService against the real PostgreSQL repositories and transaction boundary.
 * Run with -Ppg-integration against a dedicated database with the Mobility and framework migrations applied.
 */
@io.quarkus.test.junit.QuarkusTest
class WalletPostgresIntegrationTest {
    @Inject WalletService wallets;
    @Inject UnitOfWork unitOfWork;
    @Inject PgPool pool;

    @Test
    void persistedReplayDoesNotApplyTheCreditTwice() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-replay-" + UUID.randomUUID();

        wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("1200"), ref, "integration")
                .await().indefinitely();
        var replay = wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("1200"), ref, "integration")
                .await().indefinitely();

        assertAmount("1200", replay.balance());
        assertEquals(1L, ledgerCount(tenant, ref));
        assertTrue(outboxCount(tenant) >= 1L, "the credit should emit an outbox event");
        long matchingHistoryEntries = wallets.history(tenant, OwnerKind.RIDER, owner, 20)
                .await().indefinitely().stream().filter(entry -> ref.equals(entry.ref())).count();
        assertEquals(1L, matchingHistoryEntries);
    }

    @Test
    void concurrentRequestsWithTheSameReferenceProduceOneMovement() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-race-" + UUID.randomUUID();

        Uni.combine().all().unis(
                wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("2500"), ref, "integration"),
                wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("2500"), ref, "integration"))
                .asTuple().await().indefinitely();

        assertAmount("2500", wallets.view(tenant, OwnerKind.RIDER, owner)
                .await().indefinitely().balance());
        assertEquals(1L, ledgerCount(tenant, ref));
    }

    @Test
    void reusingReferenceWithDifferentAmountIsRejectedWithoutChangingTheLedger() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-conflict-" + UUID.randomUUID();
        wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("1000"), ref, "integration")
                .await().indefinitely();

        assertThrows(BusinessRuleViolation.class, () -> wallets.credit(tenant, OwnerKind.RIDER, owner,
                new BigDecimal("2000"), ref, "integration").await().indefinitely());

        assertAmount("1000", wallets.view(tenant, OwnerKind.RIDER, owner)
                .await().indefinitely().balance());
        assertEquals(1L, ledgerCount(tenant, ref));
    }

    @Test
    void outerUnitOfWorkFailureRollsBackWalletAndLedgerTogether() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-rollback-" + UUID.randomUUID();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                unitOfWork.execute(() ->
                        wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("900"), ref, "integration")
                                .flatMap(ignored -> Uni.createFrom().<WalletView>failure(
                                        new IllegalStateException("injected wallet transaction failure"))))
                        .await().indefinitely());
        assertTrue(failure.getMessage().contains("injected wallet transaction failure"));

        assertAmount("0", wallets.view(tenant, OwnerKind.RIDER, owner)
                .await().indefinitely().balance());
        assertEquals(0L, ledgerCount(tenant, ref));
        assertEquals(0L, outboxCount(tenant));
    }

    @Test
    void walletAndLedgerReadsRemainTenantScoped() {
        UUID owner = UUID.randomUUID();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String ref = "it-tenant-" + UUID.randomUUID();

        wallets.credit(tenantA, OwnerKind.RIDER, owner, new BigDecimal("700"), ref, "tenant A")
                .await().indefinitely();

        assertAmount("0", wallets.view(tenantB, OwnerKind.RIDER, owner)
                .await().indefinitely().balance());
        assertEquals(0L, ledgerCount(tenantB, ref));
        assertEquals(1L, ledgerCount(tenantA, ref));
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected monetary amount " + expected + " but was " + actual);
    }

    private long ledgerCount(UUID tenantId, String ref) {
        return pool.withTransaction(connection -> setTenant(connection, tenantId)
                .flatMap(ignored -> connection.preparedQuery(
                                "SELECT count(*) AS n FROM wallet_ledger WHERE tenant_id=$1 AND ref=$2")
                        .execute(Tuple.of(tenantId, ref))
                        .map(rows -> rows.iterator().next().getLong("n"))))
                .await().indefinitely();
    }

    private long outboxCount(UUID tenantId) {
        return pool.withTransaction(connection -> setTenant(connection, tenantId)
                .flatMap(ignored -> connection.preparedQuery(
                                "SELECT count(*) AS n FROM platform_outbox WHERE tenant_id=$1")
                        .execute(Tuple.of(tenantId))
                        .map(rows -> rows.iterator().next().getLong("n"))))
                .await().indefinitely();
    }

    private static Uni<Void> setTenant(SqlConnection connection, UUID tenantId) {
        return connection.preparedQuery("SELECT set_config('app.tenant_id', $1, true)")
                .execute(Tuple.of(tenantId.toString())).replaceWithVoid();
    }
}
