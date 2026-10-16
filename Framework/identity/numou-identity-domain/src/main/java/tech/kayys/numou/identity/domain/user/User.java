package tech.kayys.syirkah.identity.domain.user;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.identity.domain.event.UserActivated;
import tech.kayys.syirkah.identity.domain.event.UserDeactivated;
import tech.kayys.syirkah.identity.domain.event.UserEmailChanged;
import tech.kayys.syirkah.identity.domain.event.UserRegistered;
import tech.kayys.syirkah.identity.domain.exception.UserAlreadyActiveException;
import tech.kayys.syirkah.identity.domain.exception.UserAlreadyDeactivatedException;
import tech.kayys.syirkah.identity.domain.valueobject.DisplayName;
import tech.kayys.syirkah.identity.domain.valueobject.EmailAddress;
import tech.kayys.syirkah.identity.domain.valueobject.PasswordHash;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A platform user - authentication identity only.
 *
 * Deliberately does NOT know about tenants, organizations, roles, or
 * permissions. Multi-tenancy and authorization are Organization/
 * Authorization concerns that compose with Identity from the outside
 * (for organizations, the OrganizationMembership aggregate links
 * this UserId to an OrganizationId) rather than living inside this
 * aggregate.
 * Keeping User this narrow is what lets it be reused unmodified across
 * POS, e-commerce, and marketplace products with very different
 * tenancy models.
 */
public final class User extends AbstractAggregateRoot<UserId> {

    private final UserId id;
    private EmailAddress email;
    private PasswordHash passwordHash;
    private DisplayName displayName;
    private UserStatus status;

    private User(
            UserId id,
            EmailAddress email,
            PasswordHash passwordHash,
            DisplayName displayName,
            UserStatus status
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.email = Objects.requireNonNull(email, "email cannot be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    /**
     * Registers a brand-new user. Starts PENDING_VERIFICATION - a
     * separate use case (email verification) transitions it to
     * ACTIVE. Uniqueness of the email is NOT checked here: that
     * requires querying other users, which is a repository/application
     * concern, not something a single aggregate instance can enforce
     * on itself.
     */
    public static User register(
            UserId id,
            EmailAddress email,
            PasswordHash passwordHash,
            DisplayName displayName,
            Instant occurredAt
    ) {
        var user = new User(
                id,
                email,
                passwordHash,
                displayName,
                UserStatus.PENDING_VERIFICATION
        );

        user.raise(new UserRegistered(
                UUID.randomUUID(),
                occurredAt,
                id,
                email.value()
        ));

        return user;
    }

    /**
     * Reconstitutes a user from persisted state. No events are raised
     * here - rebuilding an aggregate from storage is a repository/
     * mapper concern, not a domain transition that happened "now".
     */
    public static User reconstitute(
            UserId id,
            EmailAddress email,
            PasswordHash passwordHash,
            DisplayName displayName,
            UserStatus status
    ) {
        return new User(id, email, passwordHash, displayName, status);
    }

    public void activate(Instant occurredAt) {
        if (status == UserStatus.ACTIVE) {
            throw new UserAlreadyActiveException();
        }

        status = UserStatus.ACTIVE;

        raise(new UserActivated(UUID.randomUUID(), occurredAt, id));
    }

    public void changeEmail(EmailAddress newEmail, Instant occurredAt) {
        Objects.requireNonNull(newEmail, "newEmail cannot be null");

        if (email.equals(newEmail)) {
            return;
        }

        var previous = email;
        email = newEmail;

        raise(new UserEmailChanged(
                UUID.randomUUID(),
                occurredAt,
                id,
                previous.value(),
                newEmail.value()
        ));
    }

    public void deactivate(String reason, Instant occurredAt) {
        if (status == UserStatus.DEACTIVATED) {
            throw new UserAlreadyDeactivatedException();
        }

        status = UserStatus.DEACTIVATED;

        raise(new UserDeactivated(UUID.randomUUID(), occurredAt, id, reason));
    }

    @Override
    public UserId id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public DisplayName displayName() {
        return displayName;
    }

    public UserStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

}
