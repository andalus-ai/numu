package tech.kayys.numou.notification.adapter.quarkus.jdbc;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.application.port.PushTokenProtector;
import tech.kayys.numou.notification.persistence.jdbc.PushTokenKeyProvider;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.postgresql.ds.PGSimpleDataSource;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationJdbcCompositionTest {
    private final NotificationJdbcComposition composition = new NotificationJdbcComposition();

    @Test
    void composesEncryptedProtectorFromHostManagedKeyProvider() {
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        PushTokenKeyProvider provider = new PushTokenKeyProvider() {
            @Override public KeyMaterial activeKey() {
                return new KeyMaterial("host-managed-key-v3", key);
            }
            @Override public Optional<SecretKey> keyFor(String keyId) {
                return "host-managed-key-v3".equals(keyId) ? Optional.of(key) : Optional.empty();
            }
        };

        PushTokenProtector protector = composition.pushTokenProtector(provider);
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        var encrypted = protector.protect(tenant, recipient, "provider-secret");

        assertEquals("host-managed-key-v3", encrypted.keyId());
        assertNotEquals("provider-secret", encrypted.ciphertext());
        assertEquals("provider-secret",
                protector.reveal(tenant, recipient, encrypted.keyId(), encrypted.ciphertext()));
        assertThrows(IllegalStateException.class,
                () -> protector.reveal(UUID.randomUUID(), recipient, encrypted.keyId(), encrypted.ciphertext()));
    }

    @Test
    void composesJdbcRepositoryFromHostDatasourceAndProtector() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        PushTokenProtector protector = new PushTokenProtector() {
            @Override public EncryptedToken protect(UUID tenantId, UUID recipientId, String plaintextToken) {
                return new EncryptedToken("host-key", "ciphertext");
            }
            @Override public String reveal(UUID tenantId, UUID recipientId, String keyId, String ciphertext) {
                return "not-used";
            }
        };

        PushDeviceRepository repository = composition.pushDeviceRepository(dataSource, protector);
        assertNotNull(repository);
    }

    @Test
    void rejectsMissingHostKeyProviderInsteadOfInventingAKey() {
        assertThrows(NullPointerException.class, () -> composition.pushTokenProtector(null));
    }
}
