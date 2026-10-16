package tech.kayys.syirkah.identity.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationErrorException;
import tech.kayys.syirkah.foundation.testing.time.FixedDomainClock;
import tech.kayys.syirkah.identity.application.support.InMemoryPasswordHasher;
import tech.kayys.syirkah.identity.application.support.InMemoryUserRepository;
import tech.kayys.syirkah.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.syirkah.identity.application.support.RecordingEventPublisher;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RegisterUserCommandHandlerTest {

    private final InMemoryUserRepository repository = new InMemoryUserRepository();
    private final RecordingEventPublisher eventPublisher = new RecordingEventPublisher();

    private final RegisterUserCommandHandler handler = new RegisterUserCommandHandler(
            repository,
            new InMemoryPasswordHasher(),
            eventPublisher,
            new PassthroughUnitOfWork(),
            FixedDomainClock.at(Instant.parse("2026-01-01T00:00:00Z"))
    );

    @Test
    void registersNewUserAndPublishesEvent() {
        var command = new RegisterUserCommand("jane@example.com", "s3cret", "Jane Doe");

        var userId = handler.handle(command).await().indefinitely();

        assertNotNull(userId);
        assertEquals(1, eventPublisher.published().size());
        assertEquals("identity.user.registered", eventPublisher.published().get(0).eventType());
    }

    @Test
    void rejectsDuplicateEmail() {
        handler.handle(new RegisterUserCommand("jane@example.com", "s3cret", "Jane Doe"))
                .await().indefinitely();

        var duplicate = new RegisterUserCommand("jane@example.com", "another", "Jane Impostor");

        var exception = assertThrows(
                ApplicationErrorException.class,
                () -> handler.handle(duplicate).await().indefinitely()
        );

        assertEquals("EMAIL_ALREADY_IN_USE", exception.error().code());
    }

}
