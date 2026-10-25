package tech.kayys.numou.omnichannel.postgres;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.omnichannel.core.WebhookReplayReservation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** PostgreSQL integration tests enabled with NUMOU_TEST_POSTGRES_URL. */
class PostgresWebhookReplayStoreTest {
    private static final String URL_ENV = "NUMOU_TEST_POSTGRES_URL";
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private String schema;
    private PGSimpleDataSource rootDataSource;
    private PGSimpleDataSource dataSource;
    private PostgresWebhookReplayStore store;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv(URL_ENV);
        assumeTrue(url != null && !url.isBlank(), "Set " + URL_ENV + " to enable PostgreSQL integration tests");
        schema = "omni_replay_test_" + UUID.randomUUID().toString().replace("-", "");
        PGSimpleDataSource pg = new PGSimpleDataSource();
        pg.setURL(url);
        String user = System.getenv("NUMOU_TEST_POSTGRES_USER");
        String password = System.getenv("NUMOU_TEST_POSTGRES_PASSWORD");
        if (user != null) pg.setUser(user);
        if (password != null) pg.setPassword(password);
        rootDataSource = pg;
        try (Connection connection = pg.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        pg.setCurrentSchema(schema);
        dataSource = pg;
        applyMigration();
        store = new PostgresWebhookReplayStore(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (rootDataSource == null) return;
        try (Connection connection = rootDataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void concurrentReservationsHaveExactlyOneWinner() throws Exception {
        var reservation = reservation("a".repeat(64), NOW, NOW.plusSeconds(600));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> store.reserve(reservation));
            var second = pool.submit(() -> store.reserve(reservation));
            boolean a = first.get(10, TimeUnit.SECONDS);
            boolean b = second.get(10, TimeUnit.SECONDS);
            assertThat(java.util.List.of(a, b).stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredReservationCanBeReclaimedAtomically() {
        assertThat(store.reserve(reservation("b".repeat(64), NOW, NOW.plusSeconds(60)))).isTrue();
        assertThat(store.reserve(reservation("b".repeat(64), NOW.plusSeconds(61), NOW.plusSeconds(120)))).isTrue();
        assertThat(store.reserve(reservation("b".repeat(64), NOW.plusSeconds(62), NOW.plusSeconds(180)))).isFalse();
    }

    @Test
    void cleanupDeletesOnlyExpiredRowsAndHonorsBatchLimit() {
        store.reserve(reservation("c".repeat(64), NOW.minusSeconds(120), NOW.minusSeconds(60)));
        store.reserve(reservation("d".repeat(64), NOW.minusSeconds(120), NOW.plusSeconds(60)));

        assertThat(store.deleteExpired(NOW, 1)).isEqualTo(1);
        assertThat(store.deleteExpired(NOW, 10)).isZero();
    }

    private WebhookReplayReservation reservation(String hash, Instant now, Instant expiresAt) {
        return new WebhookReplayReservation("test-provider", "account-1", hash, now, expiresAt);
    }

    private void applyMigration() throws IOException, SQLException {
        String resource = "db/migration/V5__add_webhook_replay_reservations.sql";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing migration resource: " + resource);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
            }
        }
    }
}
