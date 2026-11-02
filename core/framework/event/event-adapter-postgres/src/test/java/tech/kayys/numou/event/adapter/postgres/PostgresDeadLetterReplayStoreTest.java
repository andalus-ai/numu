package tech.kayys.numou.event.adapter.postgres;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.event.spi.port.DeadLetterReplayStorePort;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL integration tests enabled with NUMOU_TEST_POSTGRES_URL. */
class PostgresDeadLetterReplayStoreTest {
    private String schema;
    private PGSimpleDataSource root;
    private PGSimpleDataSource scoped;
    private PostgresDeadLetterReplayStore store;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach void setUp() throws Exception {
        String url = System.getenv("NUMOU_TEST_POSTGRES_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Set NUMOU_TEST_POSTGRES_URL to run PostgreSQL tests");
        root = new PGSimpleDataSource();
        root.setURL(url);
        String user = System.getenv("NUMOU_TEST_POSTGRES_USER");
        String password = System.getenv("NUMOU_TEST_POSTGRES_PASSWORD");
        if (user != null) root.setUser(user);
        if (password != null) root.setPassword(password);
        schema = "event_dlq_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = root.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
        }
        scoped = new PGSimpleDataSource();
        scoped.setURL(url);
        if (user != null) scoped.setUser(user);
        if (password != null) scoped.setPassword(password);
        scoped.setCurrentSchema(schema);
        try (Connection c = scoped.getConnection(); Statement s = c.createStatement();
             InputStream in = getClass().getResourceAsStream("/db/migration/V103__platform_event_dead_letter_replay.sql")) {
            if (in == null) throw new IllegalStateException("Replay migration missing");
            s.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        store = new PostgresDeadLetterReplayStore(scoped, Duration.ofMillis(150));
    }

    @AfterEach void tearDown() throws Exception {
        if (root == null) return;
        try (Connection c = root.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test void concurrentReplayRequestsHaveOneWinner() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> store.beginReplay(request("dlq-1", tenant, "a".repeat(64))).await().indefinitely());
            var b = pool.submit(() -> store.beginReplay(request("dlq-1", tenant, "a".repeat(64))).await().indefinitely());
            var first = a.get(10, TimeUnit.SECONDS);
            var second = b.get(10, TimeUnit.SECONDS);
            assertThat(java.util.List.of(first.status(), second.status()))
                    .containsExactlyInAnyOrder(DeadLetterReplayStorePort.ClaimStatus.ACQUIRED,
                            DeadLetterReplayStorePort.ClaimStatus.IN_PROGRESS);
        } finally { pool.shutdownNow(); }
    }

    @Test void expiredLeaseIsReclaimedAndOldTokenIsFenced() throws Exception {
        var first = store.beginReplay(request("dlq-2", tenant, "b".repeat(64))).await().indefinitely();
        Thread.sleep(250);
        var second = store.beginReplay(request("dlq-2", tenant, "b".repeat(64))).await().indefinitely();
        assertThat(second.status()).isEqualTo(DeadLetterReplayStorePort.ClaimStatus.ACQUIRED);
        assertThat(second.claimToken()).isNotEqualTo(first.claimToken());
        assertThatThrownBy(() -> store.completeReplay(first).await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
        store.completeReplay(second).await().indefinitely();
        var done = store.beginReplay(request("dlq-2", tenant, "b".repeat(64))).await().indefinitely();
        assertThat(done.status()).isEqualTo(DeadLetterReplayStorePort.ClaimStatus.ALREADY_COMPLETED);
    }

    @Test void sourceRecordCannotBeReboundToAnotherTenantOrPayload() {
        store.beginReplay(request("dlq-3", tenant, "c".repeat(64))).await().indefinitely();
        assertThatThrownBy(() -> store.beginReplay(request("dlq-3", UUID.randomUUID(), "c".repeat(64)))
                .await().indefinitely()).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> store.beginReplay(request("dlq-3", tenant, "d".repeat(64)))
                .await().indefinitely()).isInstanceOf(SecurityException.class);
    }

    private static DeadLetterReplayStorePort.ReplayRequest request(String id, UUID tenant, String hash) {
        return new DeadLetterReplayStorePort.ReplayRequest(id, tenant, "operator-1",
                "controlled recovery after incident", "pos.sale.completed", hash);
    }
}
