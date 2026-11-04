package tech.kayys.numou.automotiveparts.purchasing;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresReceiptPostingRequeueAuditTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @Test
    void everySuccessfulRequeueCreatesImmutableTenantScopedAuditEvent() throws Exception {
        JdbcReceiptPostingLedger ledger = new JdbcReceiptPostingLedger(dataSource);
        ledger.begin("tenant-a", "receipt-audit-1", "fingerprint-audit", 1);

        ledger.markRetryable("tenant-a", "receipt-audit-1", "fingerprint-audit", "first terminal failure", 1);
        ledger.requeue("tenant-a", "receipt-audit-1", "fingerprint-audit", "operator-1", "corrected supplier mapping");

        ledger.markRetryable("tenant-a", "receipt-audit-1", "fingerprint-audit", "second terminal failure", 1);
        ledger.requeue("tenant-a", "receipt-audit-1", "fingerprint-audit", "operator-2", "corrected warehouse mapping");

        JdbcReceiptPostingRequeueAuditRepository audit = new JdbcReceiptPostingRequeueAuditRepository(dataSource);
        var events = audit.findByReceipt("tenant-a", "receipt-audit-1", 100);
        assertEquals(2, events.size());
        assertEquals(Set.of("operator-1", "operator-2"),
                events.stream().map(ReceiptPostingRequeueAudit::actorId).collect(Collectors.toSet()));
        assertEquals(Set.of("corrected supplier mapping", "corrected warehouse mapping"),
                events.stream().map(ReceiptPostingRequeueAudit::reason).collect(Collectors.toSet()));
        assertTrue(events.stream().allMatch(e -> e.tenantId().equals("tenant-a")
                && e.receiptId().equals("receipt-audit-1")
                && e.payloadFingerprint().equals("fingerprint-audit")));
        assertTrue(audit.findByReceipt("tenant-b", "receipt-audit-1", 100).isEmpty());

        assertThrows(SQLException.class, () -> {
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement("""
                         UPDATE purchasing_receipt_inventory_posting_requeue_audit
                         SET reason = 'tampered'
                         WHERE tenant_id = 'tenant-a' AND receipt_id = 'receipt-audit-1'
                         """)) {
                statement.executeUpdate();
            }
        });
    }
}
