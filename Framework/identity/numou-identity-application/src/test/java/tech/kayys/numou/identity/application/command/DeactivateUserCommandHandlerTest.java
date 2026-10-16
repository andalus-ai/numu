package tech.kayys.syirkah.identity.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.testing.time.FixedDomainClock;
import tech.kayys.syirkah.identity.application.support.InMemoryPasswordHasher;
import tech.kayys.syirkah.identity.application.support.InMemoryUserRepository;
import tech.kayys.syirkah.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.syirkah.identity.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.identity.domain.user.UserStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DeactivateUserCommandHandlerTest {

    private final InMemoryUserRepository repository = new InMemoryUserRepository();
    private final RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
    private final FixedDomainClock clock = FixedDomainClock.at(Instant.parse("2026-01-01T00:00:00Z"));

    private final RegisterUserCommandHandler registerHandler = new RegisterUserCommandHandler(
            repository, new InMemoryPasswordHasher(), eventPublisher, new PassthroughUnitOfWork(), clock
    );

    private final DeactivateUserCommandHandler handler = new DeactivateUserCommandHandler(
            repository, eventPublisher, new PassthroughUnitOfWork(), clock
    );

    @Test
    void deactivatesExistingUser() {
        var userId = registerHandler.handle(
                new RegisterUserCommand("jane@example.com", "s3cret", "Jane Doe")
        ).await().indefinitely();

        handler.handle(new DeactivateUserCommand(userId.value(), "requested by user"))
                .await().indefinitely();

        var updated = repository.findById(userId).await().indefinitely().orElseThrow();
        assertEquals(UserStatus.DEACTIVATED, updated.status());
    }

}
