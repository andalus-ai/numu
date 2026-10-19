package tech.kayys.numou.workforce.domain.paymentaccount;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.paymentaccount.event.PaymentAccountAdded;
import tech.kayys.numou.workforce.domain.paymentaccount.event.PaymentAccountDeactivated;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.Objects;

/**
 * PaymentAccount aggregate root — stores a destination where worker funds can be settled.
 *
 * <p>Decoupled from Worker and Employment aggregates. A worker can have multiple accounts
 * (e.g. primary salary account, reimbursement account, digital wallet).
 */
public final class PaymentAccount extends AbstractAggregateRoot<PaymentAccountId> {

    private final WorkerId workerId;
    private final PaymentAccountType type;
    private PaymentAccountPurpose purpose;
    private final String accountName;
    private final String accountNumber;
    private final String providerCode; // e.g. bank swift/code or wallet provider
    private PaymentAccountStatus status;

    private PaymentAccount(
            PaymentAccountId id,
            WorkerId workerId,
            PaymentAccountType type,
            PaymentAccountPurpose purpose,
            String accountName,
            String accountNumber,
            String providerCode
    ) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.accountName = Objects.requireNonNull(accountName, "accountName must not be null");
        if (accountName.isBlank()) throw new IllegalArgumentException("accountName must not be blank");
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        if (accountNumber.isBlank()) throw new IllegalArgumentException("accountNumber must not be blank");
        this.providerCode = Objects.requireNonNull(providerCode, "providerCode must not be null");
        this.status = PaymentAccountStatus.ACTIVE;
    }

    public static PaymentAccount create(
            PaymentAccountId id,
            WorkerId workerId,
            PaymentAccountType type,
            PaymentAccountPurpose purpose,
            String accountName,
            String accountNumber,
            String providerCode
    ) {
        PaymentAccount account = new PaymentAccount(id, workerId, type, purpose, accountName, accountNumber, providerCode);
        account.raise(new PaymentAccountAdded(id, workerId, type, purpose));
        return account;
    }

    public void changePurpose(PaymentAccountPurpose newPurpose) {
        Objects.requireNonNull(newPurpose, "purpose must not be null");
        this.purpose = newPurpose;
    }

    public void deactivate() {
        if (status == PaymentAccountStatus.INACTIVE) {
            return;
        }
        this.status = PaymentAccountStatus.INACTIVE;
        raise(new PaymentAccountDeactivated(getId(), workerId));
    }

    public void activate() {
        this.status = PaymentAccountStatus.ACTIVE;
    }

    public WorkerId workerId() { return workerId; }
    public PaymentAccountType type() { return type; }
    public PaymentAccountPurpose purpose() { return purpose; }
    public String accountName() { return accountName; }
    public String accountNumber() { return accountNumber; }
    public String providerCode() { return providerCode; }
    public PaymentAccountStatus status() { return status; }
    public boolean isActive() { return status == PaymentAccountStatus.ACTIVE; }
}
