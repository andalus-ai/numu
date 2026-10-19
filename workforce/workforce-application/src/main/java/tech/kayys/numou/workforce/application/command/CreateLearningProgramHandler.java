package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.learning.LearningProgram;
import tech.kayys.numou.workforce.domain.learning.LearningProgramId;
import tech.kayys.numou.workforce.spi.port.LearningProgramRepository;

import java.util.Objects;

public class CreateLearningProgramHandler implements CommandHandler<CreateLearningProgramCommand, Result<LearningProgramId>> {

    private final LearningProgramRepository repository;
    private final EventPublisher eventPublisher;

    public CreateLearningProgramHandler(LearningProgramRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<LearningProgramId>> handle(CreateLearningProgramCommand cmd) {
        return Uni.createFrom().completionStage(repository.findByTenantAndCode(cmd.tenantId(), cmd.code()))
                .chain(optExisting -> {
                    if (optExisting.isPresent()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("PROGRAM_EXISTS", "Learning program already exists: " + cmd.code())));
                    }
                    LearningProgram program;
                    try {
                        program = LearningProgram.create(
                                LearningProgramId.generate(),
                                cmd.tenantId(),
                                cmd.code(),
                                cmd.name(),
                                cmd.description(),
                                cmd.type(),
                                cmd.estimatedDuration()
                        );
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("INVALID_PROGRAM", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(repository.save(program))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
