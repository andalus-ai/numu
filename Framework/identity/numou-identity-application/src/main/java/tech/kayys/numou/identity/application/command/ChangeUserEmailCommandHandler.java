package tech.kayys.numou.identity.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;

import java.util.Objects;

public final class ChangeUserEmailCommandHandler
        implements CommandHandler<ChangeUserEmailCommand, Void> {

    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public ChangeUserEmailCommandHandler(
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
    public Uni<Void> handle(ChangeUserEmailCommand command) {
        var id = UserId.of(command.userId());
        var newEmail = EmailAddress.of(command.newEmail());

        return unitOfWork.execute(() ->
                userRepository.findById(id).chain(maybeUser -> {
                    var user = maybeUser.orElseThrow(() ->
                            ApplicationError.of(
                                    "USER_NOT_FOUND",
                                    "User does not exist"
                            ).toException()
                    );

                    user.changeEmail(newEmail, clock.now());

                    return userRepository.save(user)
                            .chain(ignored -> eventPublisher.publish(user.pullDomainEvents()));
                })
        );
    }

}
