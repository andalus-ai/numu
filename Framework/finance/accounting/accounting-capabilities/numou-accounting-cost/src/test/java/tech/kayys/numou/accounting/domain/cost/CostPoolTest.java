package tech.kayys.syirkah.accounting.domain.cost;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CostPoolTest {

    @Test
    void pool_accumulation_and_reset() {
        var pool = new CostPool(CostPoolId.generate(), "IT-OVERHEAD", "IT Shared Support", AllocationBasis.HEADCOUNT);
        assertEquals(BigDecimal.ZERO, pool.accumulatedAmount());

        pool.accumulate(new BigDecimal("5000"));
        pool.accumulate(new BigDecimal("2500"));
        assertEquals(new BigDecimal("7500"), pool.accumulatedAmount());

        pool.clear();
        assertEquals(BigDecimal.ZERO, pool.accumulatedAmount());
    }
}
