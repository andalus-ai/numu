package tech.kayys.numou.automotiveparts.inventory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.adapter.memory.InMemoryInventoryRepository;
import tech.kayys.numou.inventory.application.TenantScopedInventoryService;
import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.domain.reservation.ReservationStatus;
import tech.kayys.numou.inventory.spi.TenantInventoryRepositoryProvider;
import tech.kayys.numou.product.domain.product.ProductId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class WorkshopPartsInventoryServiceTest {
    @Test
    void reserveIssueRetryReturnAndCancellationPreserveInventoryInvariants() {
        Map<String, InMemoryInventoryRepository> repositories = new ConcurrentHashMap<>();
        TenantInventoryRepositoryProvider provider = tenant -> repositories.computeIfAbsent(tenant, ignored -> new InMemoryInventoryRepository());
        TenantScopedInventoryService inventory = new TenantScopedInventoryService(provider);
        ProductId product = ProductId.generate();
        ItemId item = ItemId.of("product:" + product.value());
        WarehouseId warehouse = WarehouseId.of("workshop-warehouse-1");
        inventory.registerItem("tenant-a", new InventoryItem(item, "SKU-FILTER", "Oil filter",
                ValuationMethod.WEIGHTED_AVERAGE, TrackingMethod.NONE, "EA"));
        inventory.registerWarehouse("tenant-a", new Warehouse(warehouse, "Workshop stock", "ID"));
        inventory.receive("tenant-a", new InventoryCommandKey("opening", "receipt-1"), item, warehouse,
                new BigDecimal("10"), new BigDecimal("25.00"), "IDR", "OPENING-1");

        WorkshopPartsInventoryService service = new WorkshopPartsInventoryService(inventory,
                (tenant, productId) -> ItemId.of("product:" + productId.value()));
        UUID workOrder = UUID.randomUUID();
        UUID line = UUID.randomUUID();
        String key = "reserve-req-1";
        var reserveCommand = new WorkshopPartsInventoryService.ReservePartCommand("tenant-a", workOrder, line,
                product, warehouse, new BigDecimal("2"), "EA", Instant.now().plusSeconds(3600), key);
        var reservation = service.reserve(reserveCommand);
        var replayedReservation = service.reserve(reserveCommand);
        assertEquals(reservation.id(), replayedReservation.id());

        var issueCommand = new WorkshopPartsInventoryService.IssuePartCommand("tenant-a", workOrder, line,
                product, reservation.id().value(), warehouse, new BigDecimal("2"), "IDR", "issue-req-1");
        var issued = service.issue(issueCommand);
        var replayedIssue = service.issue(issueCommand);
        assertEquals(issued.id(), replayedIssue.id());
        assertEquals(0, inventory.getStockBalance("tenant-a", item, warehouse).onHand().compareTo(new BigDecimal("8")));
        assertEquals(ReservationStatus.COMMITTED, inventory.findReservation("tenant-a", reservation.id()).status());

        var returnCommand = new WorkshopPartsInventoryService.ReturnPartCommand("tenant-a", workOrder, line,
                product, warehouse, BigDecimal.ONE, new BigDecimal("25.00"), "IDR", "return-req-1");
        var returned = service.returnUnused(returnCommand);
        assertEquals(returned.id(), service.returnUnused(returnCommand).id());
        assertEquals(0, inventory.getStockBalance("tenant-a", item, warehouse).onHand().compareTo(new BigDecimal("9")));

        var cancellationReservation = service.reserve(new WorkshopPartsInventoryService.ReservePartCommand(
                "tenant-a", workOrder, UUID.randomUUID(), product, warehouse, BigDecimal.ONE, "EA",
                Instant.now().plusSeconds(3600), "reserve-req-2"));
        var released = service.releaseForCancellation("tenant-a", java.util.List.of(cancellationReservation.id().value()));
        assertEquals(ReservationStatus.RELEASED, released.getFirst().status());
        assertEquals(ReservationStatus.RELEASED,
                service.releaseForCancellation("tenant-a", java.util.List.of(cancellationReservation.id().value())).getFirst().status());
        assertEquals(0, inventory.getStockBalance("tenant-a", item, warehouse).onHand().compareTo(new BigDecimal("9")));
    }
}