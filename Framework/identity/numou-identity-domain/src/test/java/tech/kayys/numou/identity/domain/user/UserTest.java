package tech.kayys.syirkah.identity.domain.user;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.identity.domain.exception.UserAlreadyActiveException;
import tech.kayys.syirkah.identity.domain.exception.UserAlreadyDeactivatedException;
import tech.kayys.syirkah.identity.domain.valueobject.DisplayName;
import tech.kayys.syirkah.identity.domain.valueobject.EmailAddress;
import tech.kayys.syirkah.identity.domain.valueobject.PasswordHash;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private User newPendingUser() {
        return User.register(
                UserId.newId(),
                EmailAddress.of("jane@example.com"),
                PasswordHash.of("hashed-password"),
                DisplayName.of("Jane Doe"),
                NOW
        );
    }

    @Test
    void registeringRaisesUserRegisteredAndStartsPendingVerification() {
        var user = newPendingUser();

        assertEquals(UserStatus.PENDING_VERIFICATION, user.status());

        var events = user.pullDomainEvents();
        assertEquals(1, events.size());
        assertEquals("identity.user.registered", events.get(0).eventType());
    }

    @Test
    void activatingTransitionsToActiveAndRaisesEvent() {
        var user = newPendingUser();
        user.pullDomainEvents();

        user.activate(NOW);

        assertEquals(UserStatus.ACTIVE, user.status());
        assertTrue(user.isActive());

        var events = user.pullDomainEvents();
        assertEquals(1, events.size());
        assertEquals("identity.user.activated", events.get(0).eventType());
    }

    @Test
    void activatingAnAlreadyActiveUserThrows() {
        var user = newPendingUser();
        user.activate(NOW);

        assertThrows(UserAlreadyActiveException.class, () -> user.activate(NOW));
    }

    @Test
    void changingEmailRaisesEventWithPreviousAndNewAddress() {
        var user = newPendingUser();
        user.pullDomainEvents();

        user.changeEmail(EmailAddress.of("jane.doe@example.com"), NOW);

        assertEquals("jane.doe@example.com", user.email().value());

        var events = user.pullDomainEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof tech.kayys.syirkah.identity.domain.event.UserEmailChanged);
    }

    @Test
    void changingEmailToTheSameAddressRaisesNoEvent() {
        var user = newPendingUser();
        user.pullDomainEvents();

        user.changeEmail(EmailAddress.of("jane@example.com"), NOW);

        assertTrue(user.pullDomainEvents().isEmpty());
    }

    @Test
    void deactivatingRaisesEventWithReason() {
        var user = newPendingUser();
        user.pullDomainEvents();

        user.deactivate("requested by user", NOW);

        assertEquals(UserStatus.DEACTIVATED, user.status());

        var events = user.pullDomainEvents();
        assertEquals(1, events.size());
    }

    @Test
    void deactivatingAnAlreadyDeactivatedUserThrows() {
        var user = newPendingUser();
        user.deactivate("requested by user", NOW);

        assertThrows(
                UserAlreadyDeactivatedException.class,
                () -> user.deactivate("again", NOW)
        );
    }

    @Test
    void reconstituteDoesNotRaiseEvents() {
        var user = User.reconstitute(
                UserId.newId(),
                EmailAddress.of("jane@example.com"),
                PasswordHash.of("hashed-password"),
                DisplayName.of("Jane Doe"),
                UserStatus.ACTIVE
        );

        assertTrue(user.pullDomainEvents().isEmpty());
        assertTrue(user.isActive());
    }

}
