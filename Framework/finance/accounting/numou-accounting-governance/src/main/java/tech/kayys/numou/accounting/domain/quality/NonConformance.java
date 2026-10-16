package tech.kayys.syirkah.accounting.domain.quality;

import java.math.BigDecimal;
import java.util.Objects;

public final class NonConformance {
    public enum Severity { MINOR, MAJOR, CRITICAL }

    private final String id;
    private final String title;
    private final Severity severity;
    private NonConformanceDisposition disposition;

    public NonConformance(String id, String title, Severity severity) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
    }

    public void assignDisposition(DispositionType type, String approvedBy, String justification, BigDecimal costImpact) {
        this.disposition = new NonConformanceDisposition(type, approvedBy, justification, costImpact, java.time.Instant.now());
    }

    public String id() { return id; }
    public String ncrId() { return id; }
    public String title() { return title; }
    public String description() { return title; }
    public Severity severity() { return severity; }
    public NonConformanceDisposition disposition() { return disposition; }
    public boolean isDispositioned() { return disposition != null; }
}
