package tech.kayys.numou.courier.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.application.port.CourierEtaProvider;
import tech.kayys.numou.courier.application.port.CourierPlanningAccess;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EstimateShipmentEtaHandlerTest {
    private final Instant now=Instant.parse("2026-10-24T02:00:00Z");

    @Test void authorizesThenPersistsAppendOnlyEtaSnapshot() {
        ShipmentRepository shipments=mock(ShipmentRepository.class);
        CourierPlanningRepository planning=mock(CourierPlanningRepository.class);
        CourierEtaProvider provider=mock(CourierEtaProvider.class);
        CourierPlanningAccess access=mock(CourierPlanningAccess.class);
        TenantId tenant=TenantId.generate(); ShipmentId shipmentId=ShipmentId.generate();
        Shipment shipment=Shipment.draft(shipmentId,tenant,"NUM-ETA-1",
            new ShipmentAddress("Sender","+62111","Jl A",null,"Bandung",null,null,"ID"),
            new ShipmentAddress("Recipient","+62222","Jl B",null,"Jakarta",null,null,"ID"),
            ServiceLevel.STANDARD,now);
        var command=new EstimateShipmentEtaCommand(tenant,shipmentId,"dispatcher-1",now);
        when(access.assertCanEstimateEta(tenant,shipmentId,"dispatcher-1")).thenReturn(CompletableFuture.completedFuture(null));
        when(shipments.findById(tenant,shipmentId)).thenReturn(CompletableFuture.completedFuture(Optional.of(shipment)));
        when(provider.estimate(tenant,shipment,now)).thenReturn(CompletableFuture.completedFuture(
            new CourierEtaProvider.Prediction(now.plusSeconds(3600),now.plusSeconds(7200),new BigDecimal("0.85"),
                "rules-2026-10",CourierEtaEstimate.Source.RULES,"BASELINE_TRANSIT")));
        when(planning.saveEtaEstimate(eq(tenant),any())).thenAnswer(inv -> CompletableFuture.completedFuture(inv.getArgument(1)));

        var result=new EstimateShipmentEtaHandler(shipments,planning,provider,access).handle(command).toCompletableFuture().join();
        assertEquals(shipmentId,result.shipmentId());
        assertEquals(new BigDecimal("0.85"),result.confidence());
        verify(access).assertCanEstimateEta(tenant,shipmentId,"dispatcher-1");
        verify(planning).saveEtaEstimate(eq(tenant),any());
    }

    @Test void authorizationFailurePreventsShipmentLookupAndProviderCall() {
        ShipmentRepository shipments=mock(ShipmentRepository.class);
        CourierPlanningRepository planning=mock(CourierPlanningRepository.class);
        CourierEtaProvider provider=mock(CourierEtaProvider.class);
        CourierPlanningAccess access=mock(CourierPlanningAccess.class);
        TenantId tenant=TenantId.generate(); ShipmentId shipmentId=ShipmentId.generate();
        when(access.assertCanEstimateEta(tenant,shipmentId,"outsider"))
            .thenReturn(CompletableFuture.failedFuture(new SecurityException("not authorized")));
        var handler=new EstimateShipmentEtaHandler(shipments,planning,provider,access);
        assertThrows(java.util.concurrent.CompletionException.class,()->handler.handle(
            new EstimateShipmentEtaCommand(tenant,shipmentId,"outsider",now)).toCompletableFuture().join());
        verifyNoInteractions(shipments,planning,provider);
    }
}
