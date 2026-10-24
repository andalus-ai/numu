package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.courier.domain.CourierEtaEstimate;
import tech.kayys.numou.courier.domain.Shipment;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Pluggable ETA source; provider must return calibrated estimates and identify its model/rules version. */
public interface CourierEtaProvider {
    CompletionStage<Prediction> estimate(TenantId tenantId, Shipment shipment, Instant calculatedAt);

    record Prediction(Instant earliestAt, Instant latestAt, BigDecimal confidence,
                     String modelVersion, CourierEtaEstimate.Source source, String explanationCode) {
        public Prediction {
            Objects.requireNonNull(earliestAt); Objects.requireNonNull(latestAt); Objects.requireNonNull(confidence);
            Objects.requireNonNull(modelVersion); Objects.requireNonNull(source); Objects.requireNonNull(explanationCode);
        }
    }
}
