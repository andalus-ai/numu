package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.page.Page;
import tech.kayys.numou.foundation.application.page.PageRequest;
import tech.kayys.numou.identity.domain.user.User;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;

import java.util.Optional;

/**
 * Aggregate-specific repository port - not a generic Repository<T,ID>.
 * Only the operations Identity's use cases actually need. Owned by
 * this module, implemented by numou-identity-adapter.
 */
public interface UserRepository {

    Uni<Optional<User>> findById(UserId id);

    Uni<Optional<User>> findByEmail(EmailAddress email);

    Uni<Page<User>> findAll(PageRequest pageRequest);

    Uni<Void> save(User user);

}
