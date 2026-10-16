package tech.kayys.syirkah.construction.application.payment.command;

import tech.kayys.syirkah.construction.domain.payment.CertificateSummary;
import tech.kayys.syirkah.foundation.application.command.Command;
import java.util.UUID;

public record CreatePaymentCertificateCommand(
        UUID contractId,
        int certificateNumber,
        CertificateSummary summary
) implements Command {}
