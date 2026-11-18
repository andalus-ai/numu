package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Authoritative authorization port for shipment operations.
 *
 * <p>Implementations must evaluate the authenticated actor's current permissions and
 * tenant/organizational scope. A tenant identifier or shipment identifier is not proof
 * of access. Implementations must fail closed on policy-engine errors.</p>
 */
public interface CourierShipmentAccess {
    CompletionStage<Decision> authorize(Request request);

    enum Action {
        BOOK_SHIPMENT,
        READ_SHIPMENT,
        READ_TRACKING
    }

    record Request(TenantId tenantId, String actorReference, String correlationId,
                   Action action, String resourceReference) {
        public Request {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            actorReference = require(actorReference, "actorReference");
            correlationId = require(correlationId, "correlationId");
            Objects.requireNonNull(action, "action must not be null");
            resourceReference = require(resourceReference, "resourceReference");
        }
    }

    record Decision(boolean allowed, String decisionReference) {
        public Decision {
            if (decisionReference != null && decisionReference.isBlank()) {
                throw new IllegalArgumentException("decisionReference must not be blank when provided");
            }
            decisionReference = decisionReference == null ? null : decisionReference.trim();
        }
    }

    private static String require(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
