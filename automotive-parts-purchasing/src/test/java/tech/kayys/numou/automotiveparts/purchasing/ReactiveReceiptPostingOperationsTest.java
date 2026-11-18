package tech.kayys.numou.automotiveparts.purchasing;

import io.smallrye.mutiny.Uni;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class ReactiveReceiptPostingOperationsTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private JdbcReceiptPostingLedger ledger;
    private JdbcReceiptPostingOperationsRepository postings;
    private JdbcReceiptPostingRequeueAuditRepository audit;
    private ExecutorService worker;

    @BeforeEach
    void setUp() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        ledger = new JdbcReceiptPostingLedger(dataSource);
        postings = new JdbcReceiptPostingOperationsRepository(dataSource);
        audit = new JdbcReceiptPostingRequeueAuditRepository(dataSource);
        worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "receipt-posting-jdbc-worker"));
    }

    @AfterEach
    void tearDown() {
        if (worker != null) worker.shutdownNow();
    }

    @Test
    void reactiveFacadeKeepsIdentityAuthorizationAsyncAndDispatchesJdbcToWorker() {
        deadLetter("tenant-a", "receipt-reactive");
        AtomicReference<String> executionThread = new AtomicReference<>();
        var operations = operations("tenant-a", "operator-a", true, true);

        var summary = operations.summary()
                .invoke(ignored -> executionThread.set(Thread.currentThread().getName()))
                .await().atMost(Duration.ofSeconds(10));

        assertEquals(1, summary.deadLetter());
        assertEquals("receipt-posting-jdbc-worker", executionThread.get());
    }

    @Test
    void reactiveFacadeDeniesBeforeReadingOrMutatingTenantData() {
        deadLetter("tenant-a", "receipt-denied");
        var denied = operations("tenant-a", "operator-a", false, false);

        assertThrows(SecurityException.class,
                () -> denied.summary().await().atMost(Duration.ofSeconds(10)));
        assertThrows(SecurityException.class,
                () -> denied.requeue("receipt-denied", "not authorized")
                        .await().atMost(Duration.ofSeconds(10)));
        assertEquals(ReceiptPostingLedger.Status.DEAD_LETTER,
                ledger.begin("tenant-a", "receipt-denied", "fingerprint-receipt-denied").status());
    }

    @Test
    void reactiveRequeueRetainsTenantActorReasonAndImmutableAuditTrail() {
        deadLetter("tenant-a", "receipt-requeue");
        var operations = operations("tenant-a", "operator-a", true, true);

        operations.requeue("receipt-requeue", "supplier mapping corrected")
                .await().atMost(Duration.ofSeconds(10));

        assertEquals(ReceiptPostingLedger.Status.PENDING,
                ledger.begin("tenant-a", "receipt-requeue", "fingerprint-receipt-requeue").status());
        var history = audit.findByReceipt("tenant-a", "receipt-requeue", 10);
        assertEquals(1, history.size());
        assertEquals("operator-a", history.get(0).actorId());
        assertEquals("supplier mapping corrected", history.get(0).reason());
    }

    private void deadLetter(String tenant, String receiptId) {
        String fingerprint = "fingerprint-" + receiptId;
        ledger.begin(tenant, receiptId, fingerprint, 1);
        ledger.markRetryable(tenant, receiptId, fingerprint, "terminal failure", 1);
    }

    private ReactiveReceiptPostingOperations operations(
            String tenant, String actor, boolean inspectAllowed, boolean requeueAllowed) {
        CurrentReceiptPostingOperatorPort context =
                () -> new ReceiptPostingOperatorContext(tenant, actor);
        ReceiptPostingOperationsAuthorizationPort authorization =
                new ReceiptPostingOperationsAuthorizationPort() {
                    @Override
                    public Uni<Boolean> mayInspect(ReceiptPostingOperatorContext operator) {
                        return Uni.createFrom().item(inspectAllowed
                                && tenant.equals(operator.tenantId())
                                && actor.equals(operator.actorId()));
                    }

                    @Override
                    public Uni<Boolean> mayRequeue(ReceiptPostingOperatorContext operator, String receiptId) {
                        return Uni.createFrom().item(requeueAllowed
                                && tenant.equals(operator.tenantId())
                                && actor.equals(operator.actorId()));
                    }
                };
        return new ReactiveReceiptPostingOperations(
                context, authorization, postings, audit, ledger, worker);
    }
}
