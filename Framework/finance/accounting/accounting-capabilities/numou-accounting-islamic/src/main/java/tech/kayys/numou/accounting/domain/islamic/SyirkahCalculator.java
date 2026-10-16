package tech.kayys.numou.accounting.domain.islamic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic Syirkah calculation engine. It does not post journals or make
 * negligence/breach findings; those are separate approved workflows.
 */
public final class SyirkahCalculator {

    public SyirkahCalculation calculate(SyirkahContractVersion contract, SyirkahBusinessResult result) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(result, "result");
        if (!contract.contractId().equals(result.contractId())) throw new IllegalArgumentException("contract mismatch");
        if (contract.version() != result.contractVersion()) throw new IllegalArgumentException("contract version mismatch");
        if (!contract.currencyCode().equalsIgnoreCase(result.currencyCode())) throw new IllegalArgumentException("currency mismatch");
        if (result.periodStart().isBefore(contract.effectiveFrom()) || result.periodEnd().isAfter(contract.effectiveUntil())) {
            throw new IllegalArgumentException("business period falls outside contract version effective dates");
        }

        BigDecimal net = result.netResult();
        List<SyirkahAllocation> allocations;
        if (net.signum() > 0) {
            allocations = allocateByWeights(contract, net, contract.parties().stream()
                    .collect(java.util.stream.Collectors.toMap(SyirkahParty::partyId,
                            SyirkahParty::profitNisbahPercent, (a,b) -> a, LinkedHashMap::new)), "AGREED_NISBAH");
        } else if (net.signum() < 0) {
            allocations = allocateLoss(contract, net.abs());
        } else {
            allocations = contract.parties().stream()
                    .map(p -> new SyirkahAllocation(p.partyId(), BigDecimal.ZERO.setScale(currencyScale(contract.currencyCode())),
                            contract.currencyCode(), "BREAK_EVEN_NO_DISTRIBUTION"))
                    .toList();
        }
        String hash = hash(canonical(contract, result, net, allocations));
        return new SyirkahCalculation(result.resultId(), contract.contractId(), contract.version(),
                contract.shariaPolicyVersion(), contract.currencyCode(), net, allocations, hash);
    }

    private List<SyirkahAllocation> allocateLoss(SyirkahContractVersion contract, BigDecimal loss) {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        String basis;
        switch (contract.type()) {
            case MUDHARABAH -> {
                for (SyirkahParty p : contract.parties()) {
                    if (p.providesCapital()) weights.put(p.partyId(), p.capitalContribution());
                }
                basis = "MUDHARABAH_CAPITAL_PROVIDER_CAPITAL_RISK";
            }
            case MUSYARAKAH -> {
                for (SyirkahParty p : contract.parties()) weights.put(p.partyId(), p.capitalContribution());
                basis = "MUSYARAKAH_CAPITAL_CONTRIBUTION";
            }
            case INAN, ABDAN, WUJUH -> throw new UnsupportedOperationException(
                    "Loss policy for " + contract.type() + " requires an explicitly approved Sharia policy");
            default -> throw new IllegalStateException("Unhandled Syirkah type: " + contract.type());
        }
        if (weights.values().stream().mapToBigDecimal().reduce(BigDecimal.ZERO, BigDecimal::add).signum() <= 0) {
            throw new IllegalArgumentException("no positive capital base exists for loss allocation");
        }
        return allocateByWeights(contract, loss, weights, basis);
    }

    private List<SyirkahAllocation> allocateByWeights(
            SyirkahContractVersion contract, BigDecimal total, Map<String, BigDecimal> weights, String basis) {
        BigDecimal weightTotal = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightTotal.signum() <= 0) throw new IllegalArgumentException("allocation weights must sum to a positive value");
        int scale = currencyScale(contract.currencyCode());
        BigDecimal roundedTotal = total.setScale(scale, RoundingMode.UNNECESSARY);
        long totalMinor = roundedTotal.movePointRight(scale).longValueExact();

        record Part(String partyId, long minorUnits, BigDecimal remainder, int order) {}
        List<Part> parts = new ArrayList<>();
        long assigned = 0;
        int order = 0;
        for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
            BigDecimal exactMinor = BigDecimal.valueOf(totalMinor).multiply(entry.getValue())
                    .divide(weightTotal, 16, RoundingMode.DOWN);
            long floor = exactMinor.setScale(0, RoundingMode.DOWN).longValueExact();
            parts.add(new Part(entry.getKey(), floor, exactMinor.subtract(BigDecimal.valueOf(floor)), order++));
            assigned += floor;
        }
        long residual = totalMinor - assigned;
        List<Part> ranked = new ArrayList<>(parts);
        ranked.sort(Comparator.comparing(Part::remainder).reversed().thenComparingInt(Part::order));
        Map<String, Long> finalMinor = new LinkedHashMap<>();
        for (Part part : parts) finalMinor.put(part.partyId(), part.minorUnits());
        for (int i = 0; i < residual; i++) {
            Part p = ranked.get(i % ranked.size());
            finalMinor.put(p.partyId(), finalMinor.get(p.partyId()) + 1);
        }
        return weights.keySet().stream()
                .map(id -> new SyirkahAllocation(id, BigDecimal.valueOf(finalMinor.get(id), scale),
                        contract.currencyCode(), basis))
                .toList();
    }

    private static int currencyScale(String currencyCode) {
        int scale = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        if (scale < 0) throw new IllegalArgumentException("currency has no standard minor unit: " + currencyCode);
        return scale;
    }

    private static String canonical(SyirkahContractVersion c, SyirkahBusinessResult r,
                                    BigDecimal net, List<SyirkahAllocation> allocations) {
        StringBuilder b = new StringBuilder();
        b.append(c.contractId()).append('|').append(c.version()).append('|').append(c.tenantId()).append('|')
                .append(c.companyId()).append('|').append(c.currencyCode()).append('|').append(c.type()).append('|')
                .append(c.shariaPolicyVersion()).append('|').append(r.resultId()).append('|')
                .append(r.periodStart()).append('|').append(r.periodEnd()).append('|')
                .append(r.totalRevenue().toPlainString()).append('|').append(r.directCosts().toPlainString()).append('|')
                .append(r.operatingCosts().toPlainString()).append('|').append(r.otherCosts().toPlainString()).append('|')
                .append(r.evidenceReference()).append('|').append(net.toPlainString());
        allocations.forEach(a -> b.append('|').append(a.partyId()).append(':').append(a.amount().toPlainString())
                .append(':').append(a.basis()));
        return b.toString();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
