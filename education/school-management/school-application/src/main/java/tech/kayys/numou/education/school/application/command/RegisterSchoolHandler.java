package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.Npsn;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.school.domain.School;
import tech.kayys.numou.education.school.spi.SchoolRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class RegisterSchoolHandler {

    private final SchoolRepository schools;

    public RegisterSchoolHandler(SchoolRepository schools) {
        this.schools = Objects.requireNonNull(schools);
    }

    public CompletionStage<Result<SchoolId>> handle(RegisterSchoolCommand cmd) {
        School school;
        try {
            school = School.register(SchoolId.generate(), cmd.tenantId(), Npsn.of(cmd.npsn()), cmd.name(),
                    cmd.level(), cmd.curriculum(), cmd.ownership());
        } catch (IllegalArgumentException | NullPointerException e) {
            return Outcomes.failed("school.invalid", e.getMessage());
        }
        return schools.save(school).thenApply(saved -> Result.success(saved.getId()));
    }
}
