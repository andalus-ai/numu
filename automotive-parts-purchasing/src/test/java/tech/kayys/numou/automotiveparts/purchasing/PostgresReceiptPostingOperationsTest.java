package tech.kayys.numou.automotiveparts.purchasing;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresReceiptPostingOperationsTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private JdbcReceiptPostingLedger ledger;
    private JdbcReceiptPostingOperationsRepository postings;
    private JdbcReceiptPostingRequeueAuditRepository audit;

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
    }

    @Test
    void summaryAndDeadLetterPagesAreStrictlyTenantScopedAndKeysetPaginated() {
        deadLetter("tenant-a", "receipt-a");
        deadLetter("tenant-a", "receipt-b");
        deadLetter("tenant-b", "receipt-c");
        ledger.begin("tenant-a", "receipt-pending", "fingerprint-pending", 3);
        ledger.begin("tenant-a", "receipt-posted", "fingerprint-posted", 3);
        ledger.markPosted("tenant-a", "receipt-posted", "fingerprint-posted", "movement-1");

        var summary = postings.summary("tenant-a");
        assertEquals(1, summary.pending());
        assertEquals(1, summary.posted());
        assertEquals(2, summary.deadLetter());
        assertNotNull(summary.oldestDeadLetterAt());

        var first = postings.findDeadLetters("tenant-a", null, 1);
        assertEquals(1, first.items().size());
        assertNotNull(first.nextCursor());
        var second = postings.findDeadLetters("tenant-a", first.nextCursor(), 1);
        assertEquals(1, second.items().size());
        assertNull(second.nextCursor());
        assertNotEquals(first.items().get(0).receiptId(), second.items().get(0).receiptId());

        assertEquals(1, postings.findDeadLetters("tenant-b", null, 20).items().size());
        assertThrows(IllegalArgumentException.class,
                () -> postings.findDeadLetters("tenant-a", "not-a-cursor", 10));
    }

    @Test
    void operatorFacadeUsesTrustedTenantAndActorAndReturnsAuditHistory() {
        deadLetter("tenant-a", "receipt-operator");
        var operations = operations("tenant-a", "operator-1", true, true);

        var summary = operations.summary();
        assertEquals(1, summary.deadLetter());

        var detail = operations.getDeadLetter("receipt-operator", 20).orElseThrow();
        assertEquals("receipt-operator", detail.posting().receiptId());
        assertEquals("first failure", detail.posting().terminalFailureReason());
        assertTrue(detail.requeueHistory().isEmpty());

        operations.requeue("receipt-operator", "supplier mapping corrected");
        assertEquals(0, operations.summary().deadLetter());
        assertEquals(1, operations.summary().pending());

        var history = audit.findByReceipt("tenant-a", "receipt-operator", 20);
        assertEquals(1, history.size());
        assertEquals("operator-1", history.get(0).actorId());
        assertEquals("supplier mapping corrected", history.get(0).reason());
        assertTrue(operations.getDeadLetter("receipt-operator", 20).isEmpty());
    }

    @Test
    void inspectionAndRecoveryAreDeniedWhenHostAuthorizationDoesNotGrantThem() {
        deadLetter("tenant-a", "receipt-denied");
        var noInspect = operations("tenant-a", "operator-denied", false, true);
        assertThrows(SecurityException.class, noInspect::summary);
        assertThrows(SecurityException.class, () -> noInspect.listDeadLetters(null, 10));

        var inspectOnly = operations("tenant-a", "operator-inspect", true, false);
        assertThrows(SecurityException.class,
                () -> inspectOnly.requeue("receipt-denied", "try anyway"));
        assertEquals(ReceiptPostingLedger.Status.DEAD_LETTER,
                ledger.begin("tenant-a", "receipt-denied", "fingerprint-receipt-denied").status());
    }

    @Test
    void currentTenantCannotReadAnotherTenantsDeadLetterByGuessingReceiptId() {
        deadLetter("tenant-b", "secret-receipt");
        var operations = operations("tenant-a", "operator-a", true, true);
        assertTrue(operations.getDeadLetter("secret-receipt", 10).isEmpty());
        assertTrue(operations.listDeadLetters(null, 10).items().isEmpty());
    }

    private void deadLetter(String tenant, String receiptId) {
        String fingerprint = "fingerprint-" + receiptId;
        ledger.begin(tenant, receiptId, fingerprint, 1);
        ledger.markRetryable(tenant, receiptId, fingerprint, "first failure", 1);
    }

    private ReceiptPostingOperations operations(String tenant, String actor,
                                                boolean inspectAllowed, boolean requeueAllowed) {
        CurrentReceiptPostingOperatorPort context = () -> new ReceiptPostingOperatorContext(tenant, actor);
        ReceiptPostingOperationsAuthorizer authorization = new ReceiptPostingOperationsAuthorizer() {
            @Override public boolean mayInspect(String requestedTenant, String requestedActor) {
                return inspectAllowed && tenant.equals(requestedTenant) && actor.equals(requestedActor);
            }
            @Override public boolean mayRequeue(String requestedTenant, String requestedActor, String receiptId) {
                return requeueAllowed && tenant.equals(requestedTenant) && actor.equals(requestedActor);
            }
        };
        return new ReceiptPostingOperations(context, authorization, postings, audit, ledger);
    }
}
