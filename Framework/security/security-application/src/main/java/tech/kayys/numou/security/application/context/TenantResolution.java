package tech.kayys.syirkah.security.application.context;

import java.util.Objects;
import java.util.UUID;

/** Outcome of resolving which tenant a request may act in. */
public sealed interface TenantResolution permits TenantResolution.Resolved, TenantResolution.Rejected {

    record Resolved(UUID tenantId) implements TenantResolution {
        public Resolved {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
        }
    }

    /** @param reason internal reason for logs/audit - never echoed to the client */
    record Rejected(String reason) implements TenantResolution {
        public Rejected {
            Objects.requireNonNull(reason, "reason cannot be null");
        }
    }
}
