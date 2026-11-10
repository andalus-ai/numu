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

    /**
     * Compare-and-set a credential re-encoding after successful authentication.
     * The update must be applied only while the persisted hash still equals
     * the hash that was verified, preventing a concurrent password change
     * from being overwritten by a delayed legacy-login migration.
     *
     * @return true when this update changed the stored credential
     */
    default Uni<Boolean> updatePasswordHash(UserId id,
                                             tech.kayys.numou.identity.domain.valueobject.PasswordHash expectedHash,
                                             tech.kayys.numou.identity.domain.valueobject.PasswordHash replacementHash) {
        return findById(id).chain(optional -> {
            if (optional.isEmpty()
                    || !optional.get().passwordHash().equals(expectedHash)) {
                return Uni.createFrom().item(false);
            }
            User user = optional.get();
            user.upgradePasswordHash(replacementHash);
            return save(user).replaceWith(true);
        });
    }

}
