package tech.kayys.numou.construction.application.payment.command;

import tech.kayys.numou.construction.domain.payment.CertificateSummary;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.UUID;

public record CreatePaymentCertificateCommand(
        UUID contractId,
        int certificateNumber,
        CertificateSummary summary
) implements Command {}
