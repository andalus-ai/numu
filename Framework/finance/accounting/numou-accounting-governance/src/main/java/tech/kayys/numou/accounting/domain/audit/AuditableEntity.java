package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;

public final class AuditableEntity {
    private final String entityId;
    private final String name;
    private final String processType;
    private final AuditableKind kind;
    private RiskRating riskRating;
    private int riskScore;

    public AuditableEntity(String entityId, String name, String processType, String riskRating) {
        this(entityId, name, processType, AuditableKind.BUSINESS_PROCESS, parseRiskRating(riskRating), 50);
    }

    public AuditableEntity(String entityId, String name, String processType, AuditableKind kind, RiskRating riskRating, int riskScore) {
        this.entityId = Objects.requireNonNull(entityId, "entityId must not be null");
        if (entityId.isBlank()) throw new IllegalArgumentException("entityId must not be blank");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.processType = Objects.requireNonNull(processType, "processType must not be null");
        this.kind = Objects.requireNonNullElse(kind, AuditableKind.BUSINESS_PROCESS);
        this.riskRating = Objects.requireNonNullElse(riskRating, RiskRating.MEDIUM);
        this.riskScore = Math.max(0, riskScore);
    }

    private static RiskRating parseRiskRating(String r) {
        if (r == null) return RiskRating.MEDIUM;
        try {
            return RiskRating.valueOf(r.toUpperCase());
        } catch (Exception e) {
            return RiskRating.MEDIUM;
        }
    }

    public void rate(RiskRating rating, int score) {
        this.riskRating = Objects.requireNonNull(rating, "rating must not be null");
        this.riskScore = Math.max(0, score);
    }

    public String entityId() { return entityId; }
    public AuditableEntityId id() { return AuditableEntityId.of(entityId); }
    public String name() { return name; }
    public String processType() { return processType; }
    public AuditableKind kind() { return kind; }
    public String riskRating() { return riskRating.name(); }
    public RiskRating rating() { return riskRating; }
    public int riskScore() { return riskScore; }
}
