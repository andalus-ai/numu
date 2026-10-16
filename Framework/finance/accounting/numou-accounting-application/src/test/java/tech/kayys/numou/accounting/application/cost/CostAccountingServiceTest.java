package tech.kayys.syirkah.accounting.application.cost;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.cost.*;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CostAccountingServiceTest {

    @Test
    void record_cost_and_execute_headcount_allocation() {
        var service = new CostAccountingService();
        var itDept = new CostCenter(CostCenterId.generate(), "CC-IT", "IT Services", CostCenterType.SHARED_SERVICE, true);
        var salesDept = new CostCenter(CostCenterId.generate(), "CC-SALES", "Sales Branch", CostCenterType.BRANCH, true);
        var opsDept = new CostCenter(CostCenterId.generate(), "CC-OPS", "Operations", CostCenterType.DEPARTMENT, true);

        service.registerCostCenter(itDept);
        service.registerCostCenter(salesDept);
        service.registerCostCenter(opsDept);

        // Record direct cost to IT
        service.recordCost(itDept.id(), CostSource.AP_INVOICE, "5100-SOFTWARE", new BigDecimal("1200"), "USD", "INV-101");
        assertEquals(new BigDecimal("1200"), service.getTotalActualCost(itDept.id()));

        // Pool overhead and allocate by headcount (Sales: 30, Ops: 70)
        var itPool = new CostPool(CostPoolId.generate(), "POOL-IT", "Shared IT Infrastructure", AllocationBasis.HEADCOUNT);
        service.registerCostPool(itPool);
        service.accumulateToPool(itPool.id(), new BigDecimal("10000"));

        Map<CostCenterId, BigDecimal> weights = Map.of(
                salesDept.id(), new BigDecimal("30"),
                opsDept.id(), new BigDecimal("70")
        );

        var allocation = service.executeAllocation(itPool.id(), weights, "USD");
        assertEquals(new BigDecimal("3000.0000"), allocation.get(salesDept.id()));
        assertEquals(new BigDecimal("7000.0000"), allocation.get(opsDept.id()));

        assertEquals(new BigDecimal("3000.0000"), service.getTotalActualCost(salesDept.id()));
        assertEquals(new BigDecimal("7000.0000"), service.getTotalActualCost(opsDept.id()));
    }
}
