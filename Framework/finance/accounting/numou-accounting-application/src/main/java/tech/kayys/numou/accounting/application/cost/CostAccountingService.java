package tech.kayys.syirkah.accounting.application.cost;

import tech.kayys.syirkah.accounting.domain.cost.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service managing cost centers, cost pools, and overhead allocation distributions.
 */
public final class CostAccountingService {

    private final Map<CostCenterId, CostCenter> costCenters = new ConcurrentHashMap<>();
    private final Map<CostPoolId, CostPool> costPools = new ConcurrentHashMap<>();
    private final List<CostEntry> costEntries = Collections.synchronizedList(new ArrayList<>());

    public void registerCostCenter(CostCenter cc) { costCenters.put(cc.id(), cc); }
    public void registerCostPool(CostPool pool) { costPools.put(pool.id(), pool); }

    public void recordCost(CostCenterId ccId, CostSource src, String account, BigDecimal amount, String curr, String ref) {
        if (!costCenters.containsKey(ccId)) throw new IllegalArgumentException("CostCenter not found: " + ccId.value());
        costEntries.add(CostEntry.of(ccId, src, account, amount, curr, ref));
    }

    public void accumulateToPool(CostPoolId poolId, BigDecimal amount) {
        CostPool pool = costPools.get(poolId);
        if (pool == null) throw new IllegalArgumentException("CostPool not found: " + poolId.value());
        pool.accumulate(amount);
    }

    /**
     * Allocates accumulated cost pool amount to target cost centers according to ratio weights.
     * weights map: CostCenterId -> weight (e.g. Headcount, floor area, percentage).
     */
    public Map<CostCenterId, BigDecimal> executeAllocation(CostPoolId poolId, Map<CostCenterId, BigDecimal> weights, String currency) {
        CostPool pool = costPools.get(poolId);
        if (pool == null) throw new IllegalArgumentException("CostPool not found: " + poolId.value());

        BigDecimal totalPool = pool.accumulatedAmount();
        if (totalPool.signum() == 0) return Map.of();

        BigDecimal totalWeight = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.signum() <= 0) throw new IllegalArgumentException("Total allocation weight must be > 0");

        Map<CostCenterId, BigDecimal> distributions = new HashMap<>();
        for (Map.Entry<CostCenterId, BigDecimal> entry : weights.entrySet()) {
            BigDecimal share = totalPool.multiply(entry.getValue()).divide(totalWeight, 4, RoundingMode.HALF_UP);
            distributions.put(entry.getKey(), share);
            // Record distributed cost to target cost center
            recordCost(entry.getKey(), CostSource.MANUAL_JOURNAL, "OVERHEAD-ALLOCATION", share, currency, "POOL-ALLOC-" + pool.code());
        }

        pool.clear();
        return distributions;
    }

    public BigDecimal getTotalActualCost(CostCenterId ccId) {
        return costEntries.stream()
                .filter(e -> e.costCenterId().equals(ccId))
                .map(CostEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
