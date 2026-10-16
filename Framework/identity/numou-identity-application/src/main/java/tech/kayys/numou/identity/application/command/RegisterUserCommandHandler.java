package tech.kayys.numou.identity.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.domain.user.User;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.identity.domain.valueobject.DisplayName;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;

import java.util.Objects;

public final class RegisterUserCommandHandler
        implements CommandHandler<RegisterUserCommand, UserId> {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public RegisterUserCommandHandler(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            EventPublisher eventPublisher,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Uni<UserId> handle(RegisterUserCommand command) {
        var email = EmailAddress.of(command.email());

        return unitOfWork.execute(() ->
                userRepository.findByEmail(email).chain(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().failure(
                                ApplicationError.of(
                                        "EMAIL_ALREADY_IN_USE",
                                        "An account with this email already exists"
                                ).toException()
                        );
                    }

                    var user = User.register(
                            UserId.newId(),
                            email,
                            passwordHasher.hash(command.rawPassword()),
                            DisplayName.of(command.displayName()),
                            clock.now()
                    );

                    return userRepository.save(user)
                            .chain(ignored -> eventPublisher.publish(user.pullDomainEvents()))
                            .replaceWith(user.id());
                })
        );
    }

}
