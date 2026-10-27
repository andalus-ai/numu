package tech.kayys.payment.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.payment.api.CreatePaymentObligation;
import tech.kayys.payment.api.PaymentApplicationPort.IdempotencyConflictException;
import tech.kayys.payment.api.RefundStatus;
import tech.kayys.payment.api.RequestPaymentRefund;

@Testcontainers(disabledWithoutDocker = true)
class PostgresPaymentApplicationAdapterTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("numou_payment")
                    .withUsername("numou")
                    .withPassword("numou");

    private DataSource dataSource;
    private PostgresPaymentApplicationAdapter adapter;
    private ExecutorService callers;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setURL(POSTGRES.getJdbcUrl());
        postgres.setUser(POSTGRES.getUsername());
        postgres.setPassword(POSTGRES.getPassword());
        dataSource = postgres;
        adapter = new PostgresPaymentApplicationAdapter(dataSource);
        callers = Executors.newFixedThreadPool(6);
        try (InputStream migration = getClass().getResourceAsStream(
                "/db/migration/V1__payment_obligations_and_outbox.sql")) {
            if (migration == null) {
                throw new IllegalStateException("Payment schema migration was not packaged");
            }
            String sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS payment_operation_outbox CASCADE");
                statement.execute("DROP TABLE IF EXISTS payment_refund_operation CASCADE");
                statement.execute("DROP TABLE IF EXISTS payment_obligation CASCADE");
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) {
                        statement.execute(command);
                    }
                }
            }
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        callers.shutdownNow();
        assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void identicalRetryReturnsOriginalPaymentAndWritesOneOutboxEvent() throws Exception {
        CreatePaymentObligation command = command("tenant-a", "order-123", "key-1", "120.00");
        var first = adapter.createOrGet(command);
        var retry = adapter.createOrGet(command("tenant-a", "order-123", "key-1", "120.0"));

        assertThat(retry.paymentId()).isEqualTo(first.paymentId());
        assertThat(retry.status()).isEqualTo(first.status());
        assertThat(count("payment_obligation")).isEqualTo(1);
        assertThat(count("payment_operation_outbox")).isEqualTo(1);
    }

    @Test
    void keyReuseForDifferentSemanticRequestConflicts() {
        adapter.createOrGet(command("tenant-a", "order-123", "key-1", "120.00"));

        assertThatThrownBy(() -> adapter.createOrGet(
                command("tenant-a", "order-123", "key-1", "121.00")))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(count("payment_obligation")).isEqualTo(1);
    }

    @Test
    void idempotencyKeysAreTenantScopedAndReadsDoNotCrossTenant() {
        var first = adapter.createOrGet(command("tenant-a", "order-a", "same-key", "10.00"));
        var second = adapter.createOrGet(command("tenant-b", "order-b", "same-key", "10.00"));

        assertThat(second.paymentId()).isNotEqualTo(first.paymentId());
        assertThat(adapter.find("tenant-b", first.paymentId())).isEmpty();
        assertThat(adapter.find("tenant-a", first.paymentId())).isPresent();
    }

    @Test
    void concurrentIdenticalRequestsConvergeOnOnePayment() throws Exception {
        CreatePaymentObligation command = command("tenant-race", "order-race", "same-key", "42.50");
        var futures = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> CompletableFuture.supplyAsync(() -> adapter.createOrGet(command), callers))
                .toList();

        var results = futures.stream().map(future -> future.orTimeout(15, TimeUnit.SECONDS).join()).toList();
        assertThat(results).extracting(result -> result.paymentId()).containsOnly(results.get(0).paymentId());
        assertThat(count("payment_obligation")).isEqualTo(1);
        assertThat(count("payment_operation_outbox")).isEqualTo(1);
    }

    @Test
    void refundRetryIsIdempotentAndCumulativeRefundCannotExceedPayment() {
        var payment = adapter.createOrGet(command("tenant-refund", "order-refund", "payment-key", "100.00"));
        RequestPaymentRefund refund = new RequestPaymentRefund(
                "tenant-refund", payment.paymentId(), "refund-key-1", new BigDecimal("60.00"), "customer request");

        var first = adapter.requestRefund(refund);
        var retry = adapter.requestRefund(refund);
        assertThat(retry.refundId()).isEqualTo(first.refundId());
        assertThat(retry.status()).isEqualTo(RefundStatus.REQUESTED);

        assertThatThrownBy(() -> adapter.requestRefund(new RequestPaymentRefund(
                "tenant-refund", payment.paymentId(), "refund-key-2", new BigDecimal("50.00"), "another request")))
                .isInstanceOf(tech.kayys.payment.api.PaymentApplicationPort.PaymentOperationRejectedException.class);
        assertThat(count("payment_refund_operation")).isEqualTo(1);
        assertThat(count("payment_operation_outbox")).isEqualTo(2);
    }

    private long count(String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static CreatePaymentObligation command(
            String tenant, String reference, String key, String amount) {
        return new CreatePaymentObligation(tenant, key, reference, new BigDecimal(amount),
                "IDR", "integration test", Map.of("source", "test"));
    }
}
