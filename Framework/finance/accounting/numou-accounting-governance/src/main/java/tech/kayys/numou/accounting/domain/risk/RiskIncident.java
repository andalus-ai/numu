package tech.kayys.syirkah.accounting.domain.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class RiskIncident {
    public enum IncidentStatus { REPORTED, INVESTIGATING, CONTAINED, CLOSED }

    private final RiskIncidentId id;
    private final String riskId;
    private final String title;
    private final String description;
    private final BigDecimal financialLoss;
    private final LocalDate incidentDate;
    private IncidentStatus status;
    private final String reportedBy;

    public RiskIncident(RiskIncidentId id, String riskId, String title, String description,
                        BigDecimal financialLoss, String reportedBy) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.riskId = Objects.requireNonNull(riskId, "riskId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.financialLoss = Objects.requireNonNullElse(financialLoss, BigDecimal.ZERO);
        this.incidentDate = LocalDate.now();
        this.reportedBy = Objects.requireNonNull(reportedBy, "reportedBy must not be null");
        this.status = IncidentStatus.REPORTED;
    }

    public void advanceStatus(IncidentStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "newStatus must not be null");
    }

    public RiskIncidentId id() { return id; }
    public String riskId() { return riskId; }
    public String title() { return title; }
    public String description() { return description; }
    public BigDecimal financialLoss() { return financialLoss; }
    public LocalDate incidentDate() { return incidentDate; }
    public IncidentStatus status() { return status; }
    public String reportedBy() { return reportedBy; }
}
