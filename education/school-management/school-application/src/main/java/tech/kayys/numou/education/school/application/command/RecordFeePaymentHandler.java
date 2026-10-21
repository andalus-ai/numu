package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.FeeInvoiceId;
import tech.kayys.numou.education.school.spi.FeeInvoiceRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class RecordFeePaymentHandler {

    private final FeeInvoiceRepository invoices;

    public RecordFeePaymentHandler(FeeInvoiceRepository invoices) {
        this.invoices = Objects.requireNonNull(invoices);
    }

    public CompletionStage<Result<FeeInvoiceId>> handle(RecordFeePaymentCommand cmd) {
        return invoices.findById(cmd.invoiceId()).thenCompose(opt -> {
            if (opt.isEmpty() || !opt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<FeeInvoiceId>failed("school.fee.not-found", "Invoice not found");
            }
            var invoice = opt.get();
            try {
                invoice.pay(cmd.amount(), cmd.paidOn(), cmd.method());
            } catch (IllegalStateException | IllegalArgumentException | NullPointerException e) {
                return Outcomes.<FeeInvoiceId>failed("school.fee.cannot-pay", e.getMessage());
            }
            return invoices.save(invoice).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
