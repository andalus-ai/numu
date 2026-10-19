package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.compensation.CompensationTerms;
import tech.kayys.numou.workforce.domain.compensation.CompensationTermsId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface CompensationTermsRepository extends Repository<CompensationTerms, CompensationTermsId> {

    CompletionStage<List<CompensationTerms>> findByEmployment(EmploymentId employmentId);

    CompletionStage<Optional<CompensationTerms>> findEffective(EmploymentId employmentId, LocalDate date);
}
