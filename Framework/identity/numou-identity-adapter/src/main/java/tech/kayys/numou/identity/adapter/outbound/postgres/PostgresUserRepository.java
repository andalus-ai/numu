package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.foundation.application.page.Page;
import tech.kayys.syirkah.foundation.application.page.PageRequest;
import tech.kayys.syirkah.identity.application.port.UserRepository;
import tech.kayys.syirkah.identity.domain.user.User;
import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.identity.domain.user.UserStatus;
import tech.kayys.syirkah.identity.domain.valueobject.DisplayName;
import tech.kayys.syirkah.identity.domain.valueobject.EmailAddress;
import tech.kayys.syirkah.identity.domain.valueobject.PasswordHash;

import java.util.List;
import java.util.Optional;

/**
 * Implements the UserRepository port defined in syirkah-identity-application
 * on top of Hibernate Reactive Panache. Owns the ONLY mapping between
 * the persistence model (UserEntity) and the domain aggregate (User)
 * in the whole codebase.
 */
@ApplicationScoped
public class PostgresUserRepository implements UserRepository {

    @Override
    public Uni<Optional<User>> findById(UserId id) {
        return UserEntity.<UserEntity>findById(id.value())
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Optional<User>> findByEmail(EmailAddress email) {
        return UserEntity.<UserEntity>find("email", email.value())
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Page<User>> findAll(PageRequest pageRequest) {
        var query = UserEntity.<UserEntity>findAll();

        return query.page(pageRequest.page(), pageRequest.size())
                .list()
                .chain(entities -> query.count()
                        .map(total -> Page.of(
                                entities.stream().map(this::toDomain).toList(),
                                total,
                                pageRequest
                        ))
                );
    }

    @Override
    public Uni<Void> save(User user) {
        return UserEntity.<UserEntity>findById(user.id().value())
                .chain(existing -> {
                    var entity = existing != null ? existing : new UserEntity();

                    entity.id = user.id().value();
                    entity.email = user.email().value();
                    entity.passwordHash = user.passwordHash().value();
                    entity.displayName = user.displayName().value();
                    entity.status = user.status().name();

                    return Panache.getSession()
                            .chain(session -> session.persist(entity));
                });
    }

    private User toDomain(UserEntity entity) {
        return User.reconstitute(
                UserId.of(entity.id),
                EmailAddress.of(entity.email),
                PasswordHash.of(entity.passwordHash),
                DisplayName.of(entity.displayName),
                UserStatus.valueOf(entity.status)
        );
    }

}
