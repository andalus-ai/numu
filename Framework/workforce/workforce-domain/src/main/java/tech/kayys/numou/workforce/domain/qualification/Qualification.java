package tech.kayys.numou.workforce.domain.qualification;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.qualification.event.QualificationActivated;
import tech.kayys.numou.workforce.domain.qualification.event.QualificationCreated;
import tech.kayys.numou.workforce.domain.qualification.event.QualificationDeactivated;

import java.time.Instant;
import java.util.Objects;

/**
 * Qualification aggregate — a certified credential or formal qualification that
 * can be recorded against a worker (e.g., degree, professional licence, course).
 *
 * <p>Lifecycle: {@code ACTIVE} ↔ {@code INACTIVE}.
 */
public final class Qualification extends AbstractAggregateRoot<QualificationId> {

    private String code;
    private String name;
    private String issuingAuthority;
    private String description;
    private QualificationStatus status;

    private Qualification(QualificationId id, String code, String name,
                          String issuingAuthority, String description) {
        super(id);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.issuingAuthority = issuingAuthority;
        this.description = description;
        this.status = QualificationStatus.ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates and returns a new, active {@link Qualification}.
     *
     * @param id               unique identity
     * @param code             short catalogue code (e.g. "PMP", "B-ENG")
     * @param name             human-readable name
     * @param issuingAuthority organisation issuing this qualification (nullable)
     * @param description      optional description
     * @return new Qualification with {@link QualificationCreated} raised
     */
    public static Qualification create(QualificationId id, String code, String name,
                                       String issuingAuthority, String description) {
        Qualification q = new Qualification(id, code, name, issuingAuthority, description);
        q.raise(new QualificationCreated(id, code, name, issuingAuthority));
        return q;
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /** Activates the qualification catalogue entry. */
    public void activate() {
        if (status == QualificationStatus.ACTIVE) {
            return;
        }
        status = QualificationStatus.ACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new QualificationActivated(getId()));
    }

    /** Deactivates the qualification catalogue entry. */
    public void deactivate() {
        if (status == QualificationStatus.INACTIVE) {
            return;
        }
        status = QualificationStatus.INACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new QualificationDeactivated(getId()));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getIssuingAuthority() { return issuingAuthority; }
    public String getDescription() { return description; }
    public QualificationStatus getStatus() { return status; }
    public boolean isActive() { return status == QualificationStatus.ACTIVE; }
}
