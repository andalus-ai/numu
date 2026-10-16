package tech.kayys.syirkah.identity.application.query;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationErrorException;
import tech.kayys.syirkah.foundation.testing.time.FixedDomainClock;
import tech.kayys.syirkah.identity.application.command.RegisterUserCommand;
import tech.kayys.syirkah.identity.application.command.RegisterUserCommandHandler;
import tech.kayys.syirkah.identity.application.support.InMemoryPasswordHasher;
import tech.kayys.syirkah.identity.application.support.InMemoryUserRepository;
import tech.kayys.syirkah.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.syirkah.identity.application.support.RecordingEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GetUserByIdQueryHandlerTest {

    private final InMemoryUserRepository repository = new InMemoryUserRepository();

    private final RegisterUserCommandHandler registerHandler = new RegisterUserCommandHandler(
            repository,
            new InMemoryPasswordHasher(),
            new RecordingEventPublisher(),
            new PassthroughUnitOfWork(),
            FixedDomainClock.at(Instant.parse("2026-01-01T00:00:00Z"))
    );

    private final GetUserByIdQueryHandler handler = new GetUserByIdQueryHandler(repository);

    @Test
    void returnsViewForExistingUser() {
        var userId = registerHandler.handle(
                new RegisterUserCommand("jane@example.com", "s3cret", "Jane Doe")
        ).await().indefinitely();

        var view = handler.handle(new GetUserByIdQuery(userId.value())).await().indefinitely();

        assertEquals("jane@example.com", view.email());
        assertEquals("Jane Doe", view.displayName());
    }

    @Test
    void failsForUnknownUser() {
        var query = new GetUserByIdQuery(UUID.randomUUID());

        var exception = assertThrows(
                ApplicationErrorException.class,
                () -> handler.handle(query).await().indefinitely()
        );

        assertEquals("USER_NOT_FOUND", exception.error().code());
    }

}
