package tech.kayys.numou.accounting.domain.islamic;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable calculation result with source references for reproducibility. */
public record SyirkahCalculation(
        String resultId,
        String contractId,
        int contractVersion,
        String policyVersion,
        String currencyCode,
        BigDecimal netResult,
        List<SyirkahAllocation> allocations,
        String calculationHash) {

    public SyirkahCalculation {
        Objects.requireNonNull(resultId, "resultId");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(currencyCode, "currencyCode");
        Objects.requireNonNull(netResult, "netResult");
        Objects.requireNonNull(allocations, "allocations");
        Objects.requireNonNull(calculationHash, "calculationHash");
        allocations = List.copyOf(allocations);
        if (allocations.isEmpty()) throw new IllegalArgumentException("allocations must not be empty");
        if (allocations.stream().anyMatch(a -> !currencyCode.equals(a.currencyCode()))) {
            throw new IllegalArgumentException("allocation currency mismatch");
        }
        BigDecimal allocated = allocations.stream().map(SyirkahAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(netResult.abs()) != 0) {
            throw new IllegalArgumentException("allocations must reconcile to absolute net result");
        }
    }
}
