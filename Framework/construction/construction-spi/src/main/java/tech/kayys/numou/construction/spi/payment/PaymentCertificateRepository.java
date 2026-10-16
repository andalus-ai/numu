package tech.kayys.syirkah.construction.spi.payment;

import tech.kayys.syirkah.construction.domain.payment.PaymentCertificate;
import tech.kayys.syirkah.construction.domain.payment.PaymentCertificateId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface PaymentCertificateRepository extends Repository<PaymentCertificate, PaymentCertificateId> {
    CompletionStage<List<PaymentCertificate>> findByContractId(UUID contractId);
}
