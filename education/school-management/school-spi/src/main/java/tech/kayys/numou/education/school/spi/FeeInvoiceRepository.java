package tech.kayys.numou.education.school.spi;

import java.time.YearMonth;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.FeeInvoice;
import tech.kayys.numou.education.school.domain.FeeInvoiceId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface FeeInvoiceRepository extends Repository<FeeInvoice, FeeInvoiceId> {
    CompletionStage<Optional<FeeInvoice>> findByStudentAndPeriod(StudentId studentId, YearMonth period);
}
