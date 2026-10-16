package tech.kayys.numou.workforce.domain.qualification;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.qualification.event.QualificationRecorded;
import tech.kayys.numou.workforce.domain.qualification.event.WorkerQualificationRevoked;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * WorkerQualification aggregate — records that a specific worker holds a
 * specific {@link Qualification}, including issuance and optional expiry dates.
 *
 * <p>Lifecycle: {@code ACTIVE} → {@code REVOKED}.
 */
public final class WorkerQualification extends AbstractAggregateRoot<WorkerQualificationId> {

    private final WorkerId workerId;
    private final QualificationId qualificationId;
    private LocalDate issuedOn;
    private LocalDate expiresOn;
    private String certificateReference;
    private WorkerQualificationStatus status;

    private WorkerQualification(WorkerQualificationId id, WorkerId workerId,
                                 QualificationId qualificationId, LocalDate issuedOn,
                                 LocalDate expiresOn, String certificateReference) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.qualificationId = Objects.requireNonNull(qualificationId, "qualificationId must not be null");
        this.issuedOn = Objects.requireNonNull(issuedOn, "issuedOn must not be null");
        this.expiresOn = expiresOn; // nullable — some qualifications don't expire
        this.certificateReference = certificateReference;
        this.status = WorkerQualificationStatus.ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Records a qualification for a worker.
     *
     * @param id                   unique identity
     * @param workerId             the worker holding the qualification
     * @param qualificationId      the qualification being recorded
     * @param issuedOn             date the qualification was issued
     * @param expiresOn            optional expiry date (null = does not expire)
     * @param certificateReference optional reference number or credential ID
     * @return new WorkerQualification with {@link QualificationRecorded} raised
     */
    public static WorkerQualification create(WorkerQualificationId id, WorkerId workerId,
                                              QualificationId qualificationId, LocalDate issuedOn,
                                              LocalDate expiresOn, String certificateReference) {
        WorkerQualification wq = new WorkerQualification(id, workerId, qualificationId,
                issuedOn, expiresOn, certificateReference);
        wq.raise(new QualificationRecorded(id, workerId, qualificationId, issuedOn, expiresOn));
        return wq;
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if this qualification has passed its expiry date.
     *
     * @param asOf the reference date (usually today)
     */
    public boolean isExpired(LocalDate asOf) {
        return expiresOn != null && asOf.isAfter(expiresOn);
    }

    /**
     * Revokes this qualification record (e.g., certification withdrawn or expired).
     */
    public void revoke() {
        if (status == WorkerQualificationStatus.REVOKED) {
            return;
        }
        status = WorkerQualificationStatus.REVOKED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new WorkerQualificationRevoked(getId(), workerId, qualificationId));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public WorkerId getWorkerId() { return workerId; }
    public QualificationId getQualificationId() { return qualificationId; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public String getCertificateReference() { return certificateReference; }
    public WorkerQualificationStatus getStatus() { return status; }
    public boolean isActive() { return status == WorkerQualificationStatus.ACTIVE; }
}
