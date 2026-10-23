package tech.kayys.numou.fulfillment.application;

import tech.kayys.numou.fulfillment.domain.FulfillmentOrder;

import java.util.Objects;

public record FulfillmentCommitResult(FulfillmentOrder order, boolean replayed) {
    public FulfillmentCommitResult {
        Objects.requireNonNull(order, "order is required");
    }
}
