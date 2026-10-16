package tech.kayys.syirkah.identity.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationErrorException;
import tech.kayys.syirkah.foundation.testing.time.FixedDomainClock;
import tech.kayys.syirkah.identity.application.support.InMemoryPasswordHasher;
import tech.kayys.syirkah.identity.application.support.InMemoryUserRepository;
import tech.kayys.syirkah.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.syirkah.identity.application.support.RecordingEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChangeUserEmailCommandHandlerTest {

    private final InMemoryUserRepository repository = new InMemoryUserRepository();
    private final RecordingEventPublisher eventPublisher = new RecordingEventPublisher();
    private final FixedDomainClock clock = FixedDomainClock.at(Instant.parse("2026-01-01T00:00:00Z"));

    private final RegisterUserCommandHandler registerHandler = new RegisterUserCommandHandler(
            repository, new InMemoryPasswordHasher(), eventPublisher, new PassthroughUnitOfWork(), clock
    );

    private final ChangeUserEmailCommandHandler handler = new ChangeUserEmailCommandHandler(
            repository, eventPublisher, new PassthroughUnitOfWork(), clock
    );

    @Test
    void changesEmailForExistingUser() {
        var userId = registerHandler.handle(
                new RegisterUserCommand("jane@example.com", "s3cret", "Jane Doe")
        ).await().indefinitely();

        handler.handle(new ChangeUserEmailCommand(userId.value(), "jane.doe@example.com"))
                .await().indefinitely();

        var updated = repository.findById(userId).await().indefinitely().orElseThrow();
        assertEquals("jane.doe@example.com", updated.email().value());
    }

    @Test
    void failsForUnknownUser() {
        var command = new ChangeUserEmailCommand(UUID.randomUUID(), "someone@example.com");

        var exception = assertThrows(
                ApplicationErrorException.class,
                () -> handler.handle(command).await().indefinitely()
        );

        assertEquals("USER_NOT_FOUND", exception.error().code());
    }

}
