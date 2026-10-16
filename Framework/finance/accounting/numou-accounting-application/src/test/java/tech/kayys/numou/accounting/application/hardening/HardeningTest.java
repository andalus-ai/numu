package tech.kayys.syirkah.accounting.application.hardening;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.hardening.AuditAction;
import tech.kayys.syirkah.accounting.domain.hardening.SoDViolationException;

import static org.junit.jupiter.api.Assertions.*;

class HardeningTest {

    @Test
    void idempotency_guard_prevents_duplicate_runs() {
        var guard = new IdempotencyGuard();
        String key = "req-tx-1001";

        assertTrue(guard.acquire(key));
        assertFalse(guard.acquire(key)); // second try blocked
        assertTrue(guard.isProcessed(key));
    }

    @Test
    void sod_enforcer_blocks_self_approval() {
        assertThrows(SoDViolationException.class, () ->
                SoDEnforcer.verifySeparateUsers("alice", "alice", "POST_JOURNAL"));

        assertDoesNotThrow(() ->
                SoDEnforcer.verifySeparateUsers("alice", "bob", "POST_JOURNAL"));
    }

    @Test
    void audit_trail_records_and_retrieves_records() {
        var audit = new AuditTrailService();
        audit.record("operator-1", AuditAction.CREATE, "JournalEntry", "JE-10", "Created journal");
        audit.record("approver-1", AuditAction.APPROVE, "JournalEntry", "JE-10", "Approved journal");

        var trails = audit.findByAggregate("JournalEntry", "JE-10");
        assertEquals(2, trails.size());
        assertEquals("operator-1", trails.get(0).principal());
        assertEquals(AuditAction.APPROVE, trails.get(1).action());
    }
}
