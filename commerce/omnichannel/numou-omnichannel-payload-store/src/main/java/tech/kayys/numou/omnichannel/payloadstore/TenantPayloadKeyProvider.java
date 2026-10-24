package tech.kayys.numou.omnichannel.payloadstore;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import javax.crypto.SecretKey;

/** Supplies a tenant-scoped AES key from a KMS, HSM, or secret manager. */
@FunctionalInterface
public interface TenantPayloadKeyProvider {
    /** Production implementations must not hard-code keys or persist them beside payloads. */
    SecretKey keyFor(TenantId tenantId);
}