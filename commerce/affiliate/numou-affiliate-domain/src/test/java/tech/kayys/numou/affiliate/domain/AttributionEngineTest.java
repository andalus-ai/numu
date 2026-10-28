package tech.kayys.numou.affiliate.domain;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class AttributionEngineTest {
    private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant CONVERTED = Instant.parse("2026-10-10T12:00:00Z");
    private static final String TENANT = "tenant-a";
    private static final UUID PROGRAM = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private AttributionPolicy policy(AttributionModel model, long window) {
        return new AttributionPolicy(UUID.randomUUID(), TENANT, PROGRAM, 3, model, window, CREATED, CREATED);
    }
    private AffiliateTouchpoint touch(String id, String tenant, UUID program, String subject, Instant at) {
        return new AffiliateTouchpoint(UUID.fromString(id), tenant, program, UUID.randomUUID(), UUID.randomUUID(), subject, at);
    }
    private ConversionReference conversion(String subject) {
        return new ConversionReference("marketplace.order", "order-123", "event-1", subject, CONVERTED);
    }
    @Test void lastTouchSelectsMostRecentEligibleAndSnapshotsPolicyVersion() {
        var first = touch("30000000-0000-0000-0000-000000000001", TENANT, PROGRAM, "subject-hash", CONVERTED.minusSeconds(3600));
        var last = touch("30000000-0000-0000-0000-000000000002", TENANT, PROGRAM, "subject-hash", CONVERTED.minusSeconds(120));
        var result = AttributionEngine.decide(UUID.randomUUID(), TENANT, policy(AttributionModel.LAST_TOUCH, 7200),
                conversion("subject-hash"), List.of(first, last), CONVERTED.plusSeconds(1));
        assertEquals(AttributionOutcome.ATTRIBUTED, result.outcome());
        assertEquals(last.id(), result.touchpointId());
        assertEquals(last.partnerId(), result.partnerId());
        assertEquals(3, result.policyVersion());
    }
    @Test void firstTouchSelectsOldestEligibleTouchpoint() {
        var first = touch("30000000-0000-0000-0000-000000000001", TENANT, PROGRAM, "subject-hash", CONVERTED.minusSeconds(3600));
        var last = touch("30000000-0000-0000-0000-000000000002", TENANT, PROGRAM, "subject-hash", CONVERTED.minusSeconds(120));
        var result = AttributionEngine.decide(UUID.randomUUID(), TENANT, policy(AttributionModel.FIRST_TOUCH, 7200),
                conversion("subject-hash"), List.of(last, first), CONVERTED.plusSeconds(1));
        assertEquals(first.id(), result.touchpointId());
    }
    @Test void excludesOtherTenantsProgramsFutureAndExpiredTouchpoints() {
        var wrongTenant = touch("30000000-0000-0000-0000-000000000001", "tenant-b", PROGRAM, "subject-hash", CONVERTED.minusSeconds(20));
        var wrongProgram = touch("30000000-0000-0000-0000-000000000002", TENANT, UUID.randomUUID(), "subject-hash", CONVERTED.minusSeconds(10));
        var tooOld = touch("30000000-0000-0000-0000-000000000003", TENANT, PROGRAM, "subject-hash", CONVERTED.minusSeconds(9000));
        var future = touch("30000000-0000-0000-0000-000000000004", TENANT, PROGRAM, "subject-hash", CONVERTED.plusSeconds(1));
        var result = AttributionEngine.decide(UUID.randomUUID(), TENANT, policy(AttributionModel.LAST_TOUCH, 7200),
                conversion("subject-hash"), List.of(wrongTenant, wrongProgram, tooOld, future), CONVERTED.plusSeconds(1));
        assertEquals(AttributionOutcome.UNATTRIBUTED, result.outcome());
        assertNull(result.partnerId());
    }
    @Test void tieBreakIsStableAndPolicyMustMatchTenantAndBeEffective() {
        var a = touch("30000000-0000-0000-0000-000000000001", TENANT, PROGRAM, "subject-hash", CONVERTED.minusSeconds(10));
        var b = touch("30000000-0000-0000-0000-000000000002", TENANT, PROGRAM, "subject-hash", CONVERTED.minusSeconds(10));
        var result = AttributionEngine.decide(UUID.randomUUID(), TENANT, policy(AttributionModel.LAST_TOUCH, 7200),
                conversion("subject-hash"), List.of(b, a), CONVERTED.plusSeconds(1));
        assertEquals(b.id(), result.touchpointId());
        var wrongTenant = new AttributionPolicy(UUID.randomUUID(), "tenant-b", PROGRAM, 1, AttributionModel.LAST_TOUCH, 3600, CREATED, CREATED);
        assertThrows(IllegalArgumentException.class, () -> AttributionEngine.decide(UUID.randomUUID(), TENANT,
                wrongTenant, conversion("subject-hash"), List.of(), CONVERTED.plusSeconds(1)));
        var futurePolicy = new AttributionPolicy(UUID.randomUUID(), TENANT, PROGRAM, 1, AttributionModel.LAST_TOUCH, 3600, CONVERTED.plusSeconds(10), CREATED);
        assertThrows(IllegalArgumentException.class, () -> AttributionEngine.decide(UUID.randomUUID(), TENANT,
                futurePolicy, conversion("subject-hash"), List.of(), CONVERTED.plusSeconds(1)));
    }
}
