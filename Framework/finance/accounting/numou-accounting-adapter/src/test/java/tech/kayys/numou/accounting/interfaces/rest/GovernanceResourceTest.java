package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.hardening.AuditTrailService;
import tech.kayys.syirkah.accounting.application.hardening.IdempotencyGuard;
import tech.kayys.syirkah.accounting.application.hardening.SoDEnforcer;
import tech.kayys.syirkah.accounting.domain.hardening.AuditAction;
import tech.kayys.syirkah.accounting.domain.hardening.AuditRecord;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceResourceTest {

    private GovernanceResource resource;
    private AuditTrailService auditService;
    private SoDEnforcer sodEnforcer;
    private IdempotencyGuard idempotencyGuard;

    @BeforeEach
    void setUp() {
        auditService = new AuditTrailService();
        sodEnforcer = new SoDEnforcer();
        idempotencyGuard = new IdempotencyGuard();

        resource = new GovernanceResource();
        resource.auditTrailService = auditService;
        resource.sodEnforcer = sodEnforcer;
        resource.idempotencyGuard = idempotencyGuard;
    }

    @Test
    void testAuditTrailAndSoD() {
        auditService.record("alice", AuditAction.CREATE, "JournalEntry", "JE-101", "Created manual entry");

        List<AuditRecord> records = resource.getAuditTrail().await().indefinitely();
        assertEquals(1, records.size());

        // SoD valid case
        Response okResp = resource.verifySoD(new GovernanceResource.SoDCheckRequest("alice", "bob", "Approve")).await().indefinitely();
        assertEquals(200, okResp.getStatus());

        // SoD violation case (maker == checker)
        Response failResp = resource.verifySoD(new GovernanceResource.SoDCheckRequest("alice", "alice", "Approve")).await().indefinitely();
        assertEquals(400, failResp.getStatus());

        // Idempotency check
        Response idemp1 = resource.verifyIdempotency(new GovernanceResource.IdempotencyCheckRequest("KEY-123")).await().indefinitely();
        assertEquals(200, idemp1.getStatus());
        assertTrue((Boolean) ((Map<?, ?>) idemp1.getEntity()).get("acquired"));

        Response idemp2 = resource.verifyIdempotency(new GovernanceResource.IdempotencyCheckRequest("KEY-123")).await().indefinitely();
        assertEquals(200, idemp2.getStatus());
        assertFalse((Boolean) ((Map<?, ?>) idemp2.getEntity()).get("acquired"));
    }
}
