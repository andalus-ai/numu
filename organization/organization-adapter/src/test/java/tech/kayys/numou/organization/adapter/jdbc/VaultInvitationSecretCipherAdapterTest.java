package tech.kayys.numou.organization.adapter.jdbc;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationInvitationId;
import tech.kayys.numou.security.vault.spi.SecretEncryptionPort;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VaultInvitationSecretCipherAdapterTest {
    @Test
    void bindsEncryptionScopeToTenantAndInvitationAndRoundTrips() {
        UUID tenantValue = UUID.randomUUID();
        TenantId tenant = new TenantId(tenantValue);
        OrganizationInvitationId invitation = OrganizationInvitationId.generate();
        ScopeCapturingEncryption delegate = new ScopeCapturingEncryption();
        VaultInvitationSecretCipherAdapter adapter = new VaultInvitationSecretCipherAdapter(delegate);

        var encrypted = adapter.encrypt(tenant, invitation, "opaque-one-time-secret");
        assertNotEquals("opaque-one-time-secret", new String(encrypted.ciphertext(), StandardCharsets.UTF_8));
        assertEquals(tenantValue, delegate.lastScope.tenantId());
        assertEquals(invitation.value(), delegate.lastScope.secretId().value());
        assertEquals("opaque-one-time-secret", adapter.decrypt(tenant, invitation, encrypted));
        assertThrows(SecurityException.class, () -> adapter.decrypt(
                new TenantId(UUID.randomUUID()), invitation, encrypted));
    }

    private static final class ScopeCapturingEncryption implements SecretEncryptionPort {
        private EncryptionScope lastScope;

        @Override public EncryptedPayload encrypt(EncryptionScope scope, char[] plaintext) {
            lastScope = scope;
            byte[] bytes = new String(plaintext).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < bytes.length; i++) bytes[i] ^= 0x5A;
            return new EncryptedPayload("TEST-ENVELOPE", 1, "test-key", bytes);
        }

        @Override public char[] decrypt(EncryptionScope scope, String keyId, String algorithm,
                                        int formatVersion, byte[] ciphertext) {
            if (!scope.equals(lastScope)) throw new SecurityException("Encryption scope mismatch");
            byte[] clear = ciphertext.clone();
            for (int i = 0; i < clear.length; i++) clear[i] ^= 0x5A;
            return new String(clear, StandardCharsets.UTF_8).toCharArray();
        }
    }
}
