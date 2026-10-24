package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.application.port.CourierEtaProvider;
import tech.kayys.numou.courier.application.port.CourierPlanningAccess;
import tech.kayys.numou.courier.domain.CourierEtaEstimate;
import tech.kayys.numou.courier.domain.CourierPlanningRepository;
import tech.kayys.numou.courier.domain.ShipmentRepository;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Authorizes, calculates and appends an ETA snapshot without mutating promised delivery dates. */
public final class EstimateShipmentEtaHandler {
    private final ShipmentRepository shipments;
    private final CourierPlanningRepository planning;
    private final CourierEtaProvider provider;
    private final CourierPlanningAccess access;

    public EstimateShipmentEtaHandler(ShipmentRepository shipments, CourierPlanningRepository planning,
                                      CourierEtaProvider provider, CourierPlanningAccess access) {
        this.shipments=Objects.requireNonNull(shipments); this.planning=Objects.requireNonNull(planning);
        this.provider=Objects.requireNonNull(provider); this.access=Objects.requireNonNull(access);
    }

    public CompletionStage<CourierEtaEstimate> handle(EstimateShipmentEtaCommand command) {
        Objects.requireNonNull(command);
        return access.assertCanEstimateEta(command.tenantId(), command.shipmentId(), command.actorReference())
            .thenCompose(ignored -> shipments.findById(command.tenantId(), command.shipmentId()))
            .thenCompose(found -> {
                if (found.isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("shipment not found for tenant"));
                var shipment=found.get();
                return provider.estimate(command.tenantId(), shipment, command.calculatedAt()).thenCompose(prediction -> {
                    CourierEtaEstimate estimate=new CourierEtaEstimate(UUID.randomUUID(), command.tenantId(), command.shipmentId(),
                        prediction.earliestAt(), prediction.latestAt(), command.calculatedAt(), prediction.confidence(),
                        prediction.modelVersion(), prediction.source(), prediction.explanationCode());
                    return planning.saveEtaEstimate(command.tenantId(), estimate);
                });
            });
    }
}
