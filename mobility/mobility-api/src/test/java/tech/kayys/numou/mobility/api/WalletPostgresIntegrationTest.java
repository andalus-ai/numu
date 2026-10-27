package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.aggregate.persistence.TenantTransaction;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.wallet.application.WalletService;
import tech.kayys.numou.mobility.wallet.application.WalletView;
import tech.kayys.numou.mobility.wallet.domain.OwnerKind;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(replay.balance()).isEqualByComparingTo("1200");
        assertThat(ledgerCount(tenant, ref)).isEqualTo(1);
        assertThat(wallets.history(tenant, OwnerKind.RIDER, owner, 20).await().indefinitely())
                .filteredOn(entry -> ref.equals(entry.ref())).hasSize(1);
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

        assertThat(wallets.view(tenant, OwnerKind.RIDER, owner).await().indefinitely().balance())
                .isEqualByComparingTo("2500");
        assertThat(ledgerCount(tenant, ref)).isEqualTo(1);
    }

    @Test
    void reusingReferenceWithDifferentAmountIsRejectedWithoutChangingTheLedger() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-conflict-" + UUID.randomUUID();
        wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("1000"), ref, "integration")
                .await().indefinitely();

        assertThatThrownBy(() -> wallets.credit(tenant, OwnerKind.RIDER, owner,
                new BigDecimal("2000"), ref, "integration").await().indefinitely())
                .isInstanceOf(BusinessRuleViolation.class);

        assertThat(wallets.view(tenant, OwnerKind.RIDER, owner).await().indefinitely().balance())
                .isEqualByComparingTo("1000");
        assertThat(ledgerCount(tenant, ref)).isEqualTo(1);
    }

    @Test
    void outerUnitOfWorkFailureRollsBackWalletAndLedgerTogether() {
        UUID tenant = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String ref = "it-rollback-" + UUID.randomUUID();

        assertThatThrownBy(() -> unitOfWork.execute(() ->
                wallets.credit(tenant, OwnerKind.RIDER, owner, new BigDecimal("900"), ref, "integration")
                        .flatMap(ignored -> Uni.createFrom().<WalletView>failure(
                                new IllegalStateException("injected wallet transaction failure"))))
                .await().indefinitely())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected wallet transaction failure");

        assertThat(wallets.view(tenant, OwnerKind.RIDER, owner).await().indefinitely().balance())
                .isEqualByComparingTo("0");
        assertThat(ledgerCount(tenant, ref)).isZero();
    }

    @Test
    void walletAndLedgerReadsRemainTenantScoped() {
        UUID owner = UUID.randomUUID();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String ref = "it-tenant-" + UUID.randomUUID();

        wallets.credit(tenantA, OwnerKind.RIDER, owner, new BigDecimal("700"), ref, "tenant A")
                .await().indefinitely();

        assertThat(wallets.view(tenantB, OwnerKind.RIDER, owner).await().indefinitely().balance())
                .isEqualByComparingTo("0");
        assertThat(ledgerCount(tenantB, ref)).isZero();
        assertThat(ledgerCount(tenantA, ref)).isEqualTo(1);
    }

    private long ledgerCount(UUID tenantId, String ref) {
        return pool.withTransaction(connection -> setTenant(connection, tenantId)
                .flatMap(ignored -> connection.preparedQuery(
                                "SELECT count(*) AS n FROM wallet_ledger WHERE tenant_id=$1 AND ref=$2")
                        .execute(Tuple.of(tenantId, ref))
                        .map(rows -> rows.iterator().next().getLong("n"))))
                .await().indefinitely();
    }

    private static Uni<Void> setTenant(SqlConnection connection, UUID tenantId) {
        return connection.preparedQuery("SELECT set_config('app.tenant_id', $1, true)")
                .execute(Tuple.of(tenantId.toString())).replaceWithVoid();
    }
}
