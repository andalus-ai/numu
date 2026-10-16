package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.paymentaccount.PaymentAccountPurpose;
import tech.kayys.numou.workforce.domain.paymentaccount.PaymentAccountType;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.Objects;

public record AddPaymentAccountCommand(
        WorkerId workerId,
        PaymentAccountType type,
        PaymentAccountPurpose purpose,
        String accountName,
        String accountNumber,
        String providerCode
) implements Command {
    public AddPaymentAccountCommand {
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(accountName, "accountName must not be null");
        Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        Objects.requireNonNull(providerCode, "providerCode must not be null");
    }
}
