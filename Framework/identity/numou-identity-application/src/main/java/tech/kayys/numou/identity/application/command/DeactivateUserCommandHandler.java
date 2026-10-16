package tech.kayys.syirkah.identity.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.identity.application.port.UserRepository;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.util.Objects;

public final class DeactivateUserCommandHandler
        implements CommandHandler<DeactivateUserCommand, Void> {

    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public DeactivateUserCommandHandler(
            UserRepository userRepository,
            EventPublisher eventPublisher,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Uni<Void> handle(DeactivateUserCommand command) {
        var id = UserId.of(command.userId());

        return unitOfWork.execute(() ->
                userRepository.findById(id).chain(maybeUser -> {
                    var user = maybeUser.orElseThrow(() ->
                            ApplicationError.of(
                                    "USER_NOT_FOUND",
                                    "User does not exist"
                            ).toException()
                    );

                    user.deactivate(command.reason(), clock.now());

                    return userRepository.save(user)
                            .chain(ignored -> eventPublisher.publish(user.pullDomainEvents()));
                })
        );
    }

}
