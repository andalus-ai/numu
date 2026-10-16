
package tech.kayys.syirkah.accounting.domain.treasury;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PaymentBatch {

    public record PaymentItem(String paymentId, String beneficiaryName, String beneficiaryIban, Money amount) {}

    private final String batchId;
    private final String bankAccountId;
    private PaymentBatchStatus status;
    private final List<PaymentItem> items = new ArrayList<>();
    private String approvedBy;

    public PaymentBatch(String batchId, String bankAccountId) {
        this.batchId = Objects.requireNonNull(batchId);
        this.bankAccountId = Objects.requireNonNull(bankAccountId);
        this.status = PaymentBatchStatus.DRAFT;
    }

    public void addItem(PaymentItem item) {
        if (status != PaymentBatchStatus.DRAFT) {
            throw new IllegalStateException("Cannot add items to batch in status: " + status);
        }
        items.add(Objects.requireNonNull(item));
    }

    public Money totalBatchAmount(String currencyCode) {
        Money total = Money.zero(currencyCode);
        for (PaymentItem item : items) {
            total = total.add(item.amount());
        }
        return total;
    }

    public void approve(String approvedBy) {
        if (status != PaymentBatchStatus.DRAFT) {
            throw new IllegalStateException("Batch must be DRAFT to approve");
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot approve empty payment batch");
        }
        this.approvedBy = Objects.requireNonNull(approvedBy);
        this.status = PaymentBatchStatus.APPROVED;
    }

    public void execute() {
        if (status != PaymentBatchStatus.APPROVED) {
            throw new IllegalStateException("Batch must be APPROVED to execute");
        }
        this.status = PaymentBatchStatus.EXECUTED;
    }

    public String batchId() { return batchId; }
    public String bankAccountId() { return bankAccountId; }
    public PaymentBatchStatus status() { return status; }
    public List<PaymentItem> items() { return Collections.unmodifiableList(items); }
    public String approvedBy() { return approvedBy; }
}
