package tech.kayys.syirkah.construction.domain.payment;

import tech.kayys.syirkah.construction.domain.payment.event.PaymentCertificateApproved;
import tech.kayys.syirkah.construction.domain.payment.event.PaymentCertificateCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PaymentCertificate extends AbstractAggregateRoot<PaymentCertificateId> {
    private final UUID contractId;
    private final int certificateNumber;
    private CertificateSummary summary;
    private PaymentCertificateStatus status;

    private PaymentCertificate(PaymentCertificateId id, UUID contractId, int certificateNumber, CertificateSummary summary) {
        super(id);
        this.contractId = Objects.requireNonNull(contractId, "Contract id cannot be null");
        this.certificateNumber = certificateNumber;
        this.summary = Objects.requireNonNull(summary, "Summary cannot be null");
        this.status = PaymentCertificateStatus.DRAFT;
    }

    public static PaymentCertificate create(UUID contractId, int certificateNumber, CertificateSummary summary) {
        var cert = new PaymentCertificate(PaymentCertificateId.generate(), contractId, certificateNumber, summary);
        cert.raise(new PaymentCertificateCreated(UUID.randomUUID(), Instant.now(), cert.id().value(), contractId, certificateNumber));
        return cert;
    }

    public void certify() {
        if (status != PaymentCertificateStatus.DRAFT && status != PaymentCertificateStatus.SUBMITTED) {
            throw new IllegalStateException("Certificate cannot be certified from " + status);
        }
        status = PaymentCertificateStatus.CERTIFIED;
        raise(new PaymentCertificateApproved(UUID.randomUUID(), Instant.now(), id().value(), contractId));
    }

    public UUID contractId() { return contractId; }
    public int certificateNumber() { return certificateNumber; }
    public CertificateSummary summary() { return summary; }
    public PaymentCertificateStatus status() { return status; }
}
