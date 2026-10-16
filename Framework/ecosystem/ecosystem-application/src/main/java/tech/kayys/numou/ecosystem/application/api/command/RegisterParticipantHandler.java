package tech.kayys.syirkah.ecosystem.application.api.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.ecosystem.application.port.ParticipantRepository;
import tech.kayys.syirkah.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.syirkah.ecosystem.domain.model.Participant;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;

import java.util.Objects;

/**
 * Registers a participant and publishes its domain event atomically.
 *
 * <p>The orchestration is reactive (Uni) while the Participant aggregate
 * stays plain synchronous Java - that split is the point of the
 * foundation application layer.
 */
public final class RegisterParticipantHandler
        implements CommandHandler<RegisterParticipantCommand, Result<RegisterParticipantResult>> {

    private final ParticipantRepository participants;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public RegisterParticipantHandler(
            ParticipantRepository participants,
            EventPublisher eventPublisher,
            UnitOfWork unitOfWork) {
        this.participants = Objects.requireNonNull(participants, "participants cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public Uni<Result<RegisterParticipantResult>> handle(RegisterParticipantCommand command) {
        return unitOfWork.execute(() -> participants.existsByCode(command.code())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Uni.createFrom().item(Result.<RegisterParticipantResult>failure(
                                ApplicationError.of(
                                        "ECOSYSTEM_PARTICIPANT_CODE_TAKEN",
                                        "A participant with code '" + command.code() + "' already exists")
                        ));
                    }

                    final var participant = Participant.register(
                            ParticipantId.generate(),
                            command.code(),
                            command.name(),
                            command.type(),
                            command.organizationId(),
                            command.tenantId()
                    );

                    final var events = participant.pullDomainEvents();

                    return participants.save(participant)
                            .flatMap(saved -> eventPublisher.publish(events)
                                    .replaceWith(Result.success(new RegisterParticipantResult(
                                            saved.getId().value(),
                                            saved.getStatus().name()
                                    ))));
                }));
    }
}
