
package tech.kayys.syirkah.accounting.domain.consolidation;

import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ConsolidationRun {

    private final String runId;
    private final TenantRef parentTenantId;
    private final LedgerId consolidationLedgerId;
    private final String fiscalYear;
    private final int fiscalPeriod;
    private final LocalDate asOfDate;
    private ConsolidationRunStatus status;
    private final List<EliminationEntry> eliminations = new ArrayList<>();

    public ConsolidationRun(
            String runId,
            TenantRef parentTenantId,
            LedgerId consolidationLedgerId,
            String fiscalYear,
            int fiscalPeriod,
            LocalDate asOfDate) {
        this.runId = Objects.requireNonNull(runId);
        this.parentTenantId = Objects.requireNonNull(parentTenantId);
        this.consolidationLedgerId = Objects.requireNonNull(consolidationLedgerId);
        this.fiscalYear = Objects.requireNonNull(fiscalYear);
        this.fiscalPeriod = fiscalPeriod;
        this.asOfDate = Objects.requireNonNull(asOfDate);
        this.status = ConsolidationRunStatus.DRAFT;
    }

    public void startCollection() {
        if (status != ConsolidationRunStatus.DRAFT) {
            throw new IllegalStateException("Cannot start collection from status: " + status);
        }
        this.status = ConsolidationRunStatus.COLLECTING;
    }

    public void addElimination(EliminationEntry entry) {
        if (status != ConsolidationRunStatus.COLLECTING && status != ConsolidationRunStatus.ELIMINATING) {
            throw new IllegalStateException("Cannot add eliminations in status: " + status);
        }
        this.status = ConsolidationRunStatus.ELIMINATING;
        eliminations.add(Objects.requireNonNull(entry));
    }

    public void completeEliminations() {
        if (status != ConsolidationRunStatus.ELIMINATING) {
            throw new IllegalStateException("Not in ELIMINATING status");
        }
        this.status = ConsolidationRunStatus.TRANSLATING;
    }

    public void closeConsolidation() {
        this.status = ConsolidationRunStatus.CLOSING;
    }

    public void publish() {
        if (status != ConsolidationRunStatus.CLOSING) {
            throw new IllegalStateException("Consolidation must be in CLOSING status to publish");
        }
        this.status = ConsolidationRunStatus.PUBLISHED;
    }

    public String runId() { return runId; }
    public TenantRef parentTenantId() { return parentTenantId; }
    public LedgerId consolidationLedgerId() { return consolidationLedgerId; }
    public String fiscalYear() { return fiscalYear; }
    public int fiscalPeriod() { return fiscalPeriod; }
    public LocalDate asOfDate() { return asOfDate; }
    public ConsolidationRunStatus status() { return status; }
    public List<EliminationEntry> eliminations() { return Collections.unmodifiableList(eliminations); }
}
