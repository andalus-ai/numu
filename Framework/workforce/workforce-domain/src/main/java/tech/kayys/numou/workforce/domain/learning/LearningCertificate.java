package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.learning.event.LearningCertificateIssued;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class LearningCertificate extends AbstractAggregateRoot<LearningCertificateId> {

    private final LearningEnrollmentId enrollmentId;
    private final String certificateNumber;
    private final LocalDate issuedDate;
    private final LocalDate expiryDate;
    private final String issuerRef;
    private final String documentRef;
    private LearningCertificateStatus status;

    private LearningCertificate(
            LearningCertificateId id,
            LearningEnrollmentId enrollmentId,
            String certificateNumber,
            LocalDate issuedDate,
            LocalDate expiryDate,
            String issuerRef,
            String documentRef
    ) {
        super(id);
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId must not be null");
        this.certificateNumber = Objects.requireNonNull(certificateNumber, "certificateNumber must not be null");
        this.issuedDate = Objects.requireNonNull(issuedDate, "issuedDate must not be null");
        this.expiryDate = expiryDate;
        this.issuerRef = issuerRef;
        this.documentRef = documentRef;
        this.status = LearningCertificateStatus.VALID;
    }

    public static LearningCertificate issue(
            LearningCertificateId id,
            LearningEnrollmentId enrollmentId,
            String certificateNumber,
            LocalDate issuedDate,
            LocalDate expiryDate,
            String issuerRef,
            String documentRef
    ) {
        LearningCertificate cert = new LearningCertificate(id, enrollmentId, certificateNumber, issuedDate, expiryDate, issuerRef, documentRef);
        cert.raise(new LearningCertificateIssued(id, enrollmentId, certificateNumber));
        return cert;
    }

    public void revoke() {
        this.status = LearningCertificateStatus.REVOKED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public LearningEnrollmentId getEnrollmentId() { return enrollmentId; }
    public String getCertificateNumber() { return certificateNumber; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getIssuerRef() { return issuerRef; }
    public String getDocumentRef() { return documentRef; }
    public LearningCertificateStatus getStatus() { return status; }
}
