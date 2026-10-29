package tech.kayys.numou.transport.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.transport.domain.TransportCheckout;
import tech.kayys.numou.transport.domain.TransportCheckoutStatus;
import tech.kayys.numou.transport.spi.TransportCheckoutConcurrencyException;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PgTransportCheckoutRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-28T02:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private PgTransportCheckoutRepository repository;

    @BeforeAll
    static void createSchema() throws IOException, SQLException {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        executeMigration("/db/migration/V2__transport_checkout.sql");
        executeMigration("/db/migration/V3__transport_checkout_reconciliation_index.sql");
    }

    private static void executeMigration(String resource) throws IOException, SQLException {
        String migration;
        try (var stream = PgTransportCheckoutRepositoryTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("migration resource is missing: " + resource);
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (String sql : migration.split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
    }

    @BeforeEach
    void resetRows() throws SQLException {
        repository = new PgTransportCheckoutRepository(dataSource);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE transport_checkout");
        }
    }

    @Test
    void persistsCheckoutAndPaymentActionUri() {
        TransportCheckout initial = checkout("tenant-a", UUID.randomUUID(), "checkout-a");
        repository.create(initial);

        TransportCheckout loaded = repository.findByIdempotencyKey("tenant-a", "checkout-a").orElseThrow();
        TransportCheckout held = loaded.withHold(loaded.holdId(), NOW.plusSeconds(1));
        repository.save(held, loaded.version());
        TransportCheckout pending = held.withPayment("payment-a", "https://pay.example/action",
                TransportCheckoutStatus.PAYMENT_ACTION_REQUIRED, NOW.plusSeconds(2));
        repository.save(pending, held.version());

        TransportCheckout persisted = repository.find("tenant-a", initial.checkoutId()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(TransportCheckoutStatus.PAYMENT_ACTION_REQUIRED);
        assertThat(persisted.paymentId()).isEqualTo("payment-a");
        assertThat(persisted.paymentActionUri()).isEqualTo("https://pay.example/action");
        assertThat(repository.findByPaymentId("tenant-a", "payment-a").orElseThrow().checkoutId())
                .isEqualTo(persisted.checkoutId());
    }

    @Test
    void tenantAndIdempotencyAreScopedAndStaleWritesAreRejected() {
        TransportCheckout tenantA = checkout("tenant-a", UUID.randomUUID(), "same-key");
        TransportCheckout tenantB = checkout("tenant-b", UUID.randomUUID(), "same-key");
        repository.create(tenantA);
        repository.create(tenantB);

        assertThat(repository.findByIdempotencyKey("tenant-a", "same-key").orElseThrow().checkoutId())
                .isEqualTo(tenantA.checkoutId());
        assertThat(repository.findByIdempotencyKey("tenant-b", "same-key").orElseThrow().checkoutId())
                .isEqualTo(tenantB.checkoutId());
        assertThat(repository.find("tenant-b", tenantA.checkoutId())).isEmpty();

        TransportCheckout updated = tenantA.withHold(tenantA.holdId(), NOW.plusSeconds(1));
        repository.save(updated, 0);
        assertThatThrownBy(() -> repository.save(tenantA.withHold(tenantA.holdId(), NOW.plusSeconds(2)), 0))
                .isInstanceOf(TransportCheckoutConcurrencyException.class);
    }

    @Test
    void staleQueryReturnsOnlyNonterminalCheckoutsInOldestFirstOrder() {
        TransportCheckout initial = checkout("tenant-a", UUID.randomUUID(), "due-key");
        repository.create(initial);
        TransportCheckout held = initial.withHold(initial.holdId(), NOW.plusSeconds(1));
        repository.save(held, initial.version());
        TransportCheckout pending = held.withPayment("payment-due", null,
                TransportCheckoutStatus.PAYMENT_PENDING, NOW.plusSeconds(2));
        repository.save(pending, held.version());

        assertThat(repository.findStaleNonTerminal(NOW.plusSeconds(3), 10))
                .extracting(TransportCheckout::checkoutId).containsExactly(initial.checkoutId());

        TransportCheckout failed = pending.withPayment("payment-due", null,
                TransportCheckoutStatus.PAYMENT_FAILED, NOW.plusSeconds(4));
        repository.save(failed, pending.version());
        assertThat(repository.findStaleNonTerminal(NOW.plusSeconds(10), 10)).isEmpty();
    }

    @Test
    void duplicateTenantScopedIdempotencyKeyIsRejected() {
        repository.create(checkout("tenant-a", UUID.randomUUID(), "same-key"));

        assertThatThrownBy(() -> repository.create(checkout("tenant-a", UUID.randomUUID(), "same-key")))
                .isInstanceOf(TransportCheckoutConcurrencyException.class);
    }

    private static TransportCheckout checkout(String tenant, UUID id, String key) {
        return new TransportCheckout(tenant, id, key, "a".repeat(64), UUID.randomUUID(), "quote-1",
                1, 3, 2, new java.math.BigDecimal("125000.00"), "IDR",
                NOW.plusSeconds(900), UUID.randomUUID(), null, null, null, null,
                TransportCheckoutStatus.REQUESTED, 0, NOW, NOW);
    }

    private static final class DriverManagerDataSource implements DataSource {
        private final String url;
        private final String username;
        private final String password;

        private DriverManagerDataSource(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Override public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
        @Override public Connection getConnection(String user, String pass) throws SQLException {
            return DriverManager.getConnection(url, user, pass);
        }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
