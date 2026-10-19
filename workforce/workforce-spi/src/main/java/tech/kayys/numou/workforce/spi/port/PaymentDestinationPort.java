package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.paymentaccount.PaymentAccountId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * SPI port for resolving the preferred payment destination (bank account, e-wallet, etc.)
 * for a worker during payroll disbursement.
 */
public interface PaymentDestinationPort {

    /**
     * Resolves the primary payment destination for a worker.
     *
     * @param workerId     the worker
     * @param employmentId the employment context (may affect destination selection)
     * @return the PaymentAccountId to use, or empty if no preferred destination configured
     */
    CompletionStage<Optional<PaymentAccountId>> resolvePrimaryDestination(
            WorkerId workerId,
            EmploymentId employmentId
    );

    /**
     * Validates that the given payment account is currently active and usable for disbursement.
     *
     * @param accountId the account to validate
     * @return true if valid and active
     */
    CompletionStage<Boolean> isAccountActive(PaymentAccountId accountId);
}
