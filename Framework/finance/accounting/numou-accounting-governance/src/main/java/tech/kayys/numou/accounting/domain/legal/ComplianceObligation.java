package tech.kayys.syirkah.accounting.domain.legal;

import java.time.LocalDate;
import java.util.Objects;

public final class ComplianceObligation {
    private final String obligationId;
    private final String contractId;
    private final String title;
    private final LocalDate dueDate;
    private boolean satisfied;

    public ComplianceObligation(String obligationId, String contractId, String title, LocalDate dueDate) {
        this.obligationId = Objects.requireNonNull(obligationId, "obligationId must not be null");
        this.contractId = Objects.requireNonNull(contractId, "contractId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
        this.satisfied = false;
    }

    public void satisfy() { this.satisfied = true; }

    public String obligationId() { return obligationId; }
    public String contractId() { return contractId; }
    public String title() { return title; }
    public String description() { return title; }
    public LocalDate dueDate() { return dueDate; }
    public boolean isSatisfied() { return satisfied; }
}
