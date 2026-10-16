package tech.kayys.syirkah.identity.application.support;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.page.Page;
import tech.kayys.syirkah.foundation.application.page.PageRequest;
import tech.kayys.syirkah.identity.application.port.UserRepository;
import tech.kayys.syirkah.identity.domain.user.User;
import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.identity.domain.valueobject.EmailAddress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory UserRepository test double - good enough to exercise
 * command/query handlers without a real database. Not a stand-in for
 * an integration test against the real Postgres adapter.
 */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<UserId, User> usersById = new LinkedHashMap<>();

    @Override
    public Uni<Optional<User>> findById(UserId id) {
        return Uni.createFrom().item(Optional.ofNullable(usersById.get(id)));
    }

    @Override
    public Uni<Optional<User>> findByEmail(EmailAddress email) {
        return Uni.createFrom().item(
                usersById.values().stream()
                        .filter(user -> user.email().equals(email))
                        .findFirst()
        );
    }

    @Override
    public Uni<Page<User>> findAll(PageRequest pageRequest) {
        var all = List.copyOf(usersById.values());

        var content = all.stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();

        return Uni.createFrom().item(Page.of(content, all.size(), pageRequest));
    }

    @Override
    public Uni<Void> save(User user) {
        usersById.put(user.id(), user);
        return Uni.createFrom().voidItem();
    }

}
