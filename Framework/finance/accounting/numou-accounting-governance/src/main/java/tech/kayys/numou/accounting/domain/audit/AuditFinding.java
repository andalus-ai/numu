package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;

public final class AuditFinding {
    public enum Severity {
        OBSERVATION, LOW, MINOR, MEDIUM, SIGNIFICANT, HIGH, MATERIAL, CRITICAL
    }

    private final String findingId;
    private final String engagementId;
    private final String title;
    private final String condition;
    private final String rootCause;
    private final String riskDescription;
    private final Severity severity;
    private FindingStatus status;
    private String closureNotes;

    public AuditFinding(String findingId, String engagementId, String title, String condition, Severity severity) {
        this(findingId, engagementId, title, condition, "Not specified", "Not specified", severity);
    }

    public AuditFinding(String findingId, String engagementId, String title, String condition,
                        String rootCause, String riskDescription, Severity severity) {
        this.findingId = Objects.requireNonNull(findingId, "findingId must not be null");
        this.engagementId = Objects.requireNonNull(engagementId, "engagementId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.condition = Objects.requireNonNull(condition, "condition must not be null");
        this.rootCause = Objects.requireNonNullElse(rootCause, "");
        this.riskDescription = Objects.requireNonNullElse(riskDescription, "");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.status = FindingStatus.OPEN;
        this.closureNotes = "";
    }

    public void beginRemediation() {
        if (status != FindingStatus.OPEN) {
            throw new AuditViolationException("beginRemediation requires OPEN; current=" + status);
        }
        this.status = FindingStatus.IN_REMEDIATION;
    }

    public void close(String closureNotes) {
        if (status == FindingStatus.CLOSED || status == FindingStatus.ACCEPTED_RISK) {
            throw new AuditViolationException("Finding already terminal: " + status);
        }
        this.status = FindingStatus.CLOSED;
        this.closureNotes = closureNotes == null ? "" : closureNotes;
    }

    public void acceptRisk(String rationale) {
        if (status == FindingStatus.CLOSED || status == FindingStatus.ACCEPTED_RISK) {
            throw new AuditViolationException("Finding already terminal: " + status);
        }
        this.status = FindingStatus.ACCEPTED_RISK;
        this.closureNotes = rationale == null ? "" : rationale;
    }

    public String findingId() { return findingId; }
    public FindingId id() { return FindingId.of(findingId); }
    public String engagementId() { return engagementId; }
    public String title() { return title; }
    public String condition() { return condition; }
    public String observation() { return condition; }
    public String rootCause() { return rootCause; }
    public String riskDescription() { return riskDescription; }
    public Severity severity() { return severity; }
    public FindingStatus status() { return status; }
    public String closureNotes() { return closureNotes; }
    public String recommendation() { return ""; }
}
