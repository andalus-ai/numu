package tech.kayys.syirkah.accounting.application.currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.workflow.gamelan.GamelanWorkflowClient;
import tech.kayys.syirkah.accounting.application.workflow.gamelan.InMemoryGamelanWorkflowAdapter;
import tech.kayys.syirkah.accounting.application.workflow.gamelan.WorkflowInstanceStatus;
import tech.kayys.syirkah.accounting.application.workflow.gamelan.WorkflowStartRequest;
import tech.kayys.syirkah.accounting.domain.currency.ExchangeRateProvider;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FX Revaluation & Gamelan Integration Test")
class FxRevaluationEngineTest {

    @Test
    @DisplayName("FX Revaluation computes unrealized gain correctly and produces balanced journal")
    void testFxGainRevaluation() {
        ExchangeRateProvider rateProvider = (src, tgt, date) -> Optional.of(new BigDecimal("16000")); // 1 USD = 16,000 IDR
        FxRevaluationEngine engine = new FxRevaluationEngine(rateProvider);

        TenantRef tenantId = new TenantRef("t1");
        LedgerId ledgerId = new LedgerId("PRIMARY");

        // 10,000 USD booked at 15,000 IDR = 150,000,000 IDR
        // At closing rate 16,000 IDR = 160,000,000 IDR -> Gain of 10,000,000 IDR
        var result = engine.revalueAccount(
                tenantId, ledgerId,
                AccountId.generate(),
                AccountId.generate(),
                AccountId.generate(),
                Currency.of("USD"), Currency.of("IDR"),
                new BigDecimal("10000"),
                new BigDecimal("150000000"),
                LocalDate.now(),
                "SYSTEM"
        );

        assertEquals(0, new BigDecimal("10000000").compareTo(result.gainLossAmount()));
        assertNotNull(result.adjustingJournalEntry());
        assertDoesNotThrow(() -> result.adjustingJournalEntry().post());
    }

    @Test
    @DisplayName("Gamelan workflow bridge triggers workflow process asynchronously")
    void testGamelanWorkflowBridge() {
        GamelanWorkflowClient gamelan = new InMemoryGamelanWorkflowAdapter();

        var request = new WorkflowStartRequest(
                "period-close-fx-revaluation",
                "CLOSE-2026-09",
                new TenantRef("t1"),
                new LedgerId("PRIMARY"),
                Map.of("month", "2026-09", "initiator", "cfo")
        );

        String instanceId = gamelan.startProcess(request).await().indefinitely();
        assertNotNull(instanceId);
        assertTrue(instanceId.startsWith("gamelan-proc-"));

        WorkflowInstanceStatus status = gamelan.getProcessStatus(instanceId).await().indefinitely();
        assertEquals(WorkflowInstanceStatus.RUNNING, status);
    }
}
