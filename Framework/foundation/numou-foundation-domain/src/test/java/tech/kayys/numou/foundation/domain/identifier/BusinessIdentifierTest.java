package tech.kayys.numou.foundation.domain.identifier;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BusinessIdentifierTest {

    @Test
    void shouldCreateAndValidateBusinessIdentifier() {
        BusinessIdentifier id = BusinessIdentifier.of("SALES_ORDER", "SO-2026-000042");
        assertEquals("SALES_ORDER", id.namespace());
        assertEquals("SO-2026-000042", id.value());
        assertEquals("SO-2026-000042", id.toString());

        assertThrows(IllegalArgumentException.class, () -> BusinessIdentifier.of("", "SO-01"));
        assertThrows(IllegalArgumentException.class, () -> BusinessIdentifier.of("ORDER", "  "));
    }

    @Test
    void shouldFormatIdentifierDeterministically() {
        IdentifierFormat format = IdentifierFormat.of("INV-", 6);
        assertEquals("INV-2026-000042", format.format(2026, 42));
        assertEquals("INV--000042", format.formatWithoutPeriod(42));

        assertThrows(IllegalArgumentException.class, () -> format.format(2026, 0));
        assertThrows(IllegalArgumentException.class, () -> format.format(2026, -5));
        assertThrows(IllegalArgumentException.class, () -> IdentifierFormat.of("SO-", 0));
        assertThrows(IllegalArgumentException.class, () -> IdentifierFormat.of("SO-", 20));
    }

    @Test
    void shouldProduceScopeKey() {
        TenantId tenantId = TenantId.generate();
        UUID legalEntityId = UUID.randomUUID();
        UUID businessUnitId = UUID.randomUUID();

        IdentifierScope tenantScope = IdentifierScope.ofTenant(tenantId);
        assertEquals("tenant:" + tenantId.value(), tenantScope.toScopeKey());

        IdentifierScope leScope = IdentifierScope.ofLegalEntity(tenantId, legalEntityId);
        assertEquals("tenant:" + tenantId.value() + ":le:" + legalEntityId, leScope.toScopeKey());

        IdentifierScope buScope = IdentifierScope.ofBusinessUnit(tenantId, legalEntityId, businessUnitId);
        assertEquals("tenant:" + tenantId.value() + ":le:" + legalEntityId + ":bu:" + businessUnitId, buScope.toScopeKey());
    }

    @Test
    void shouldEvaluatePolicyEffectiveness() {
        IdentifierPolicy policy = new IdentifierPolicy(
                IdentifierPolicyId.generate(),
                "SALES_ORDER",
                IdentifierFormat.of("SO-", 5),
                IdentifierPolicyStatus.ACTIVE,
                false,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertTrue(policy.isEffective(LocalDate.of(2026, 6, 1)));
        assertFalse(policy.isEffective(LocalDate.of(2025, 12, 31)));
        assertFalse(policy.isEffective(LocalDate.of(2027, 1, 1)));

        IdentifierPolicy draftPolicy = new IdentifierPolicy(
                IdentifierPolicyId.generate(),
                "SALES_ORDER",
                IdentifierFormat.of("SO-", 5),
                IdentifierPolicyStatus.DRAFT,
                false,
                null,
                null
        );
        assertFalse(draftPolicy.isEffective(LocalDate.of(2026, 6, 1)));
    }

    @Test
    void shouldRecordAllocation() {
        BusinessIdentifier id = BusinessIdentifier.of("INVOICE", "INV-2026-000100");
        IdentifierPolicyId policyId = IdentifierPolicyId.generate();
        IdentifierScope scope = IdentifierScope.ofTenant(TenantId.generate());

        IdentifierAllocation allocation = new IdentifierAllocation(id, policyId, scope, 100L, Instant.now());
        assertEquals(100L, allocation.sequenceNumber());
        assertEquals(id, allocation.identifier());
    }
}
