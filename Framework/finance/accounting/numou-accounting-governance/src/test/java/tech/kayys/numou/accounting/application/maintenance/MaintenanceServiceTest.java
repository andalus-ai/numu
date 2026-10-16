package tech.kayys.syirkah.accounting.application.maintenance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.maintenance.MaintenanceWorkOrder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MaintenanceService")
class MaintenanceServiceTest {

    private MaintenanceService svc;

    @BeforeEach
    void setUp() {
        svc = new MaintenanceService();
    }

    @Test
    @DisplayName("createWorkOrder returns DRAFT work order")
    void createWorkOrder_draft() {
        var wo = svc.createWorkOrder("ASSET-01", "Quarterly PM", LocalDate.now().plusDays(7));
        assertNotNull(wo);
        assertEquals(MaintenanceWorkOrder.Status.DRAFT, wo.status());
        assertEquals("ASSET-01", wo.assetId());
    }

    @Test
    @DisplayName("schedule → start → complete → close lifecycle")
    void workOrder_fullLifecycle() {
        var wo = svc.createWorkOrder("ASSET-02", "Oil change", LocalDate.now().plusDays(3));

        wo.schedule();
        assertEquals(MaintenanceWorkOrder.Status.SCHEDULED, wo.status());

        wo.start();
        assertEquals(MaintenanceWorkOrder.Status.IN_PROGRESS, wo.status());

        wo.complete(new BigDecimal("450.00"));
        assertEquals(MaintenanceWorkOrder.Status.COMPLETED, wo.status());
        assertEquals(new BigDecimal("450.00"), wo.actualCost());

        wo.close();
        assertEquals(MaintenanceWorkOrder.Status.CLOSED, wo.status());
    }

    @Test
    @DisplayName("complete without start throws IllegalStateException")
    void complete_withoutStart_throws() {
        var wo = svc.createWorkOrder("ASSET-03", "Belt replacement", LocalDate.now());
        wo.schedule();
        assertThrows(IllegalStateException.class, () -> wo.complete(BigDecimal.TEN));
    }

    @Test
    @DisplayName("registerSchedule returns schedule record with interval")
    void registerSchedule() {
        var schedule = svc.registerSchedule("ASSET-04", 90, "Vibration analysis");
        assertNotNull(schedule);
        assertEquals("ASSET-04", schedule.assetId());
        assertEquals(90, schedule.intervalDays());
        assertEquals("Vibration analysis", schedule.taskDescription());
    }

    @Test
    @DisplayName("getWorkOrder throws for unknown ID")
    void getWorkOrder_notFound() {
        assertThrows(IllegalArgumentException.class, () -> svc.getWorkOrder("UNKNOWN-ID"));
    }

    @Test
    @DisplayName("listWorkOrders returns all created orders")
    void listWorkOrders() {
        svc.createWorkOrder("ASSET-05", "Lube", LocalDate.now());
        svc.createWorkOrder("ASSET-06", "Filter", LocalDate.now());
        assertEquals(2, svc.listWorkOrders().size());
    }
}
