package tech.kayys.syirkah.accounting.consolidation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConsolidationRunTest {
    @Test
    void followsTheControlledPublicationLifecycle() {
        var service = new ConsolidationService();
        var run = service.start("tenant-a", LocalDate.of(2026, 12, 31), "idr");

        service.collect(run.id(), "1000", new BigDecimal("100"));
        service.eliminate(run.id(), new ConsolidationRun.Elimination(
                "parent", "subsidiary", ConsolidationRun.EliminationKind.RECIPROCAL_BALANCE,
                new BigDecimal("25"), "IDR", "IC-1"));
        service.translate(run.id());
        service.close(run.id());
        service.publish(run.id());

        assertEquals(ConsolidationRun.Status.PUBLISHED, service.require(run.id()).status());
        assertEquals(new BigDecimal("100"), service.require(run.id()).trialBalance().get("1000"));
    }

    @Test
    void rejectsUnknownRuns() {
        var service = new ConsolidationService();
        assertThrows(java.util.NoSuchElementException.class, () -> service.require(UUID.randomUUID()));
    }

    @Test
    void appliesOwnershipEliminationsTranslationAndMinorityInterest() {
        var service = new ConsolidationService();
        var run = service.start("tenant-a", LocalDate.of(2026, 12, 31), "IDR");
        var hierarchy = new ConsolidationService.GroupHierarchy("parent");
        hierarchy.add(new ConsolidationService.GroupMember("subsidiary", "parent",
                ConsolidationService.MemberType.SUBSIDIARY, new BigDecimal("0.80"), "USD"));
        service.registerGroup(run.id(), hierarchy);
        service.collect(run.id(), "1000", new BigDecimal("100"));
        service.recordIntercompany(run.id(), new ConsolidationService.IntercompanyBalance(
                "parent", "subsidiary", "INTERCOMPANY_PROFIT", new BigDecimal("25"), "IDR", "IC-2"));

        var entries = service.executeEliminations(run.id());
        assertEquals(2, entries.size());
        var translation = service.translate(run.id(), "subsidiary", Map.of("1000", new BigDecimal("10")),
                new BigDecimal("15000"));
        assertEquals(new BigDecimal("150000"), translation.balances().get("1000"));
        assertEquals(new BigDecimal("0.20"), service.calculateMinorityInterest(run.id(),
                Map.of("subsidiary", new BigDecimal("50"))).getFirst().nonControllingPercentage());
    }
}
