package tech.kayys.numou.identity.application.support;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.identity.domain.user.User;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.identity.domain.user.UserStatus;
import tech.kayys.numou.identity.domain.valueobject.DisplayName;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;
import tech.kayys.numou.identity.domain.valueobject.PasswordHash;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryUserRepositoryPasswordUpgradeTest {

    @Test
    void compareAndSetUpdatesOnlyWhenVerifiedHashIsStillCurrent() {
        var repository = new InMemoryUserRepository();
        var id = UserId.of(UUID.randomUUID());
        var oldHash = PasswordHash.of("legacy-hash");
        var newHash = PasswordHash.of("versioned-hash");
        var user = User.reconstitute(id, EmailAddress.of("security@example.test"),
                oldHash, DisplayName.of("Security Test"), UserStatus.ACTIVE);
        repository.save(user).await().indefinitely();

        boolean updated = repository.updatePasswordHash(id, oldHash, newHash)
                .await().indefinitely();

        assertTrue(updated);
        assertEquals(newHash, repository.findById(id).await().indefinitely()
                .orElseThrow().passwordHash());
    }

    @Test
    void staleMigrationCannotOverwriteAChangedPassword() {
        var repository = new InMemoryUserRepository();
        var id = UserId.of(UUID.randomUUID());
        var oldHash = PasswordHash.of("legacy-hash");
        var changedHash = PasswordHash.of("user-changed-password-hash");
        var delayedMigrationHash = PasswordHash.of("delayed-migration-hash");
        var user = User.reconstitute(id, EmailAddress.of("security@example.test"),
                oldHash, DisplayName.of("Security Test"), UserStatus.ACTIVE);
        repository.save(user).await().indefinitely();

        assertTrue(repository.updatePasswordHash(id, oldHash, changedHash)
                .await().indefinitely());
        assertFalse(repository.updatePasswordHash(id, oldHash, delayedMigrationHash)
                .await().indefinitely());

        assertEquals(changedHash, repository.findById(id).await().indefinitely()
                .orElseThrow().passwordHash());
    }
}
