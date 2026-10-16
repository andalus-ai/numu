package tech.kayys.syirkah.accounting.application.hardening;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.hardening.DomainHealthInvariants;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Platform Hardening Tests (darft-capa.md Part A)")
class BridgeHardeningTest {

    @Test
    @DisplayName("InMemoryBridgeIdempotencyRegistry prevents replay of bridge commands")
    void testBridgeIdempotency() {
        var registry = new InMemoryBridgeIdempotencyRegistry();
        String sourceEventId = "EVT-SALES-INVOICE-9911";
        String bridgeCode = "sales->ar";

        assertFalse(registry.alreadyDispatched(sourceEventId, bridgeCode));

        registry.record(sourceEventId, bridgeCode, "CreateAccountsReceivableInvoiceCommand");
        assertTrue(registry.alreadyDispatched(sourceEventId, bridgeCode));

        // Different bridge with same source event is allowed
        assertFalse(registry.alreadyDispatched(sourceEventId, "sales->commissions"));
    }

    @Test
    @DisplayName("DomainHealthInvariants validates GL and Asset consistency")
    void testDomainHealthInvariants() {
        // Balanced GL
        var glOk = DomainHealthInvariants.verifyBalance(BigDecimal.valueOf(1000.00), BigDecimal.valueOf(1000.00));
        assertTrue(glOk.healthy());

        // Out of balance GL
        var glFail = DomainHealthInvariants.verifyBalance(BigDecimal.valueOf(1000.00), BigDecimal.valueOf(999.00));
        assertFalse(glFail.healthy());

        // Valid Asset
        var assetOk = DomainHealthInvariants.verifyAssetDepreciation(
                BigDecimal.valueOf(10000.00), BigDecimal.valueOf(2000.00), BigDecimal.valueOf(8000.00));
        assertTrue(assetOk.healthy());

        // Invalid Asset
        var assetFail = DomainHealthInvariants.verifyAssetDepreciation(
                BigDecimal.valueOf(10000.00), BigDecimal.valueOf(2000.00), BigDecimal.valueOf(7500.00));
        assertFalse(assetFail.healthy());
    }
}
