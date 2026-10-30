package tech.kayys.numou.ecosystem.application.api.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.application.port.PartyRepository;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.Objects;

/** Creates canonical Party identity and publishes its event in the unit of work. */
public final class RegisterPartyHandler
        implements CommandHandler<RegisterPartyCommand, Result<RegisterPartyResult>> {

    private final PartyRepository parties;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;

    public RegisterPartyHandler(
            PartyRepository parties,
            EventPublisher eventPublisher,
            UnitOfWork unitOfWork) {
        this.parties = Objects.requireNonNull(parties, "parties cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
    }

    @Override
    public Uni<Result<RegisterPartyResult>> handle(RegisterPartyCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        return unitOfWork.execute(() -> {
            Party party = Party.register(PartyId.generate(), command.kind());
            var events = party.pullDomainEvents();
            return parties.save(party)
                    .flatMap(saved -> eventPublisher.publish(events)
                            .replaceWith(Result.success(new RegisterPartyResult(
                                    saved.getId(), saved.kind(), saved.status()))));
        });
    }
}
