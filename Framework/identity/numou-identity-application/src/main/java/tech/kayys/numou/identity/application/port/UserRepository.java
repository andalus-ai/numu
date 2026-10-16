package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.page.Page;
import tech.kayys.syirkah.foundation.application.page.PageRequest;
import tech.kayys.syirkah.identity.domain.user.User;
import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.identity.domain.valueobject.EmailAddress;

import java.util.Optional;

/**
 * Aggregate-specific repository port - not a generic Repository<T,ID>.
 * Only the operations Identity's use cases actually need. Owned by
 * this module, implemented by syirkah-identity-adapter.
 */
public interface UserRepository {

    Uni<Optional<User>> findById(UserId id);

    Uni<Optional<User>> findByEmail(EmailAddress email);

    Uni<Page<User>> findAll(PageRequest pageRequest);

    Uni<Void> save(User user);

}
