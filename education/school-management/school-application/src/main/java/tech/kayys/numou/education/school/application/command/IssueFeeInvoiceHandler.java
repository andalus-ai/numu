package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.FeeInvoice;
import tech.kayys.numou.education.school.domain.FeeInvoiceId;
import tech.kayys.numou.education.school.spi.FeeInvoiceRepository;
import tech.kayys.numou.education.school.spi.StudentRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Issues the SPP invoice for an active student; at most one non-void invoice per period. */
public class IssueFeeInvoiceHandler {

    private final StudentRepository students;
    private final FeeInvoiceRepository invoices;

    public IssueFeeInvoiceHandler(StudentRepository students, FeeInvoiceRepository invoices) {
        this.students = Objects.requireNonNull(students);
        this.invoices = Objects.requireNonNull(invoices);
    }

    public CompletionStage<Result<FeeInvoiceId>> handle(IssueFeeInvoiceCommand cmd) {
        return students.findById(cmd.studentId()).thenCompose(sOpt -> {
            if (sOpt.isEmpty() || !sOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<FeeInvoiceId>failed("school.student.not-found", "Student not found");
            }
            if (!sOpt.get().isActive()) return Outcomes.<FeeInvoiceId>failed("school.student.inactive", "Student is not active");
            return invoices.findByStudentAndPeriod(cmd.studentId(), cmd.period()).thenCompose(existing -> {
                if (existing.isPresent()) {
                    return Outcomes.<FeeInvoiceId>failed("school.fee.duplicate-period", "An invoice already exists for " + cmd.period());
                }
                FeeInvoice invoice;
                try {
                    invoice = FeeInvoice.issue(FeeInvoiceId.generate(), cmd.tenantId(), cmd.studentId(), cmd.period(), cmd.amount(), cmd.dueDate());
                } catch (IllegalArgumentException | NullPointerException e) {
                    return Outcomes.<FeeInvoiceId>failed("school.fee.invalid", e.getMessage());
                }
                return invoices.save(invoice).thenApply(saved -> Result.success(saved.getId()));
            });
        });
    }
}
