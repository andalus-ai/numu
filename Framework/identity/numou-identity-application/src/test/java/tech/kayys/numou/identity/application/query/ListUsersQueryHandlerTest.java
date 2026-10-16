package tech.kayys.syirkah.identity.application.query;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.page.PageRequest;
import tech.kayys.syirkah.foundation.testing.time.FixedDomainClock;
import tech.kayys.syirkah.identity.application.command.RegisterUserCommand;
import tech.kayys.syirkah.identity.application.command.RegisterUserCommandHandler;
import tech.kayys.syirkah.identity.application.support.InMemoryPasswordHasher;
import tech.kayys.syirkah.identity.application.support.InMemoryUserRepository;
import tech.kayys.syirkah.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.syirkah.identity.application.support.RecordingEventPublisher;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ListUsersQueryHandlerTest {

    private final InMemoryUserRepository repository = new InMemoryUserRepository();

    private final RegisterUserCommandHandler registerHandler = new RegisterUserCommandHandler(
            repository,
            new InMemoryPasswordHasher(),
            new RecordingEventPublisher(),
            new PassthroughUnitOfWork(),
            FixedDomainClock.at(Instant.parse("2026-01-01T00:00:00Z"))
    );

    private final ListUsersQueryHandler handler = new ListUsersQueryHandler(repository);

    @Test
    void listsRegisteredUsersWithPagination() {
        for (int i = 0; i < 3; i++) {
            registerHandler.handle(
                    new RegisterUserCommand("user" + i + "@example.com", "s3cret", "User " + i)
            ).await().indefinitely();
        }

        var page = handler.handle(new ListUsersQuery(PageRequest.of(0, 2))).await().indefinitely();

        assertEquals(2, page.content().size());
        assertEquals(3, page.totalElements());
        assertTrue(page.hasNext());
    }

}
