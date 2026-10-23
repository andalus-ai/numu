package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure deterministic Syirkah result engine. It never posts journals, creates
 * receivables, or determines partner negligence. Those require separate,
 * authorized application/accounting workflows.
 */
public final class SyirkahCalculationEngine {

    public SyirkahCalculationSnapshot calculate(
            String calculationId,
            SyirkahContractVersion contract,
            FinancialResult financialResult,
            List<CapitalPosition> capitalPositions,
            LossClassification lossClassification,
            List<String> evidenceIds
    ) {
        calculationId = SyirkahPartyTerms.requireText(calculationId, "calculationId");
        Objects.requireNonNull(contract, "contract must not be null");
        Objects.requireNonNull(financialResult, "financialResult must not be null");
        Objects.requireNonNull(lossClassification, "lossClassification must not be null");
        LossCause lossCause = lossClassification.cause();
        List<String> evidence = new ArrayList<>(List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds must not be null")));
        if (evidence.isEmpty()) throw new IllegalArgumentException("At least one financial-result/source evidence ID is required");
        for (String id : evidence) SyirkahPartyTerms.requireText(id, "evidenceId");
        evidence.addAll(lossClassification.evidenceIds());

        if (!contract.isEffectiveOn(financialResult.periodStart()) || !contract.isEffectiveOn(financialResult.periodEnd())) {
            throw new IllegalArgumentException("One contract version must cover the entire financial period; split the calculation when an akad version changes mid-period");
        }
        if (!contract.currencyCode().equals(financialResult.currencyCode())) {
            throw new IllegalArgumentException("Contract and financial result currencies differ");
        }
        if (contract.currencyScale() != financialResult.currencyScale()) {
            throw new IllegalArgumentException("Contract and financial result precision policies differ");
        }

        BigDecimal net = financialResult.netResult();
        List<PartnerAllocation> allocations = List.of();
        SyirkahCalculationSnapshot.ResultType resultType;
        List<CapitalPosition> positions = validateCapitalPositions(contract, financialResult, capitalPositions, net.signum() < 0);

        if (net.signum() > 0) {
            if (lossCause != LossCause.NOT_APPLICABLE) throw new IllegalArgumentException("Loss classification must be NOT_APPLICABLE for a profitable period");
            List<WeightedParty> weights = contract.parties().stream()
                    .map(p -> new WeightedParty(p.partyId(), BigInteger.valueOf(p.profitSharingRatio().basisPoints())))
                    .toList();
            allocations = allocate(net, financialResult.scale(), weights);
            resultType = SyirkahCalculationSnapshot.ResultType.PROFIT;
        } else if (net.signum() == 0) {
            if (lossCause != LossCause.NOT_APPLICABLE) throw new IllegalArgumentException("Loss classification must be NOT_APPLICABLE for a break-even period");
            resultType = SyirkahCalculationSnapshot.ResultType.BREAK_EVEN;
        } else {
            if (lossCause == LossCause.NOT_APPLICABLE) throw new IllegalArgumentException("A negative result requires an explicit loss classification");
            if (lossCause != LossCause.ORDINARY_BUSINESS && lossCause != LossCause.FORCE_MAJEURE) {
                // Never turn allegations into a partner debt or automatically alter loss allocation.
                resultType = SyirkahCalculationSnapshot.ResultType.RESPONSIBILITY_REVIEW_REQUIRED;
            } else if (contract.lossAllocationMode() == LossAllocationMode.MANUAL_REVIEW_REQUIRED) {
                resultType = SyirkahCalculationSnapshot.ResultType.POLICY_REVIEW_REQUIRED;
            } else {
                List<WeightedParty> weights = lossWeights(contract, positions);
                allocations = allocate(net.abs(), financialResult.scale(), weights);
                resultType = SyirkahCalculationSnapshot.ResultType.LOSS;
            }
        }

        List<CapitalPosition> positionsForHash = net.signum() < 0 ? positions : List.of();
        List<String> allEvidence = new ArrayList<>(evidence);
        positionsForHash.stream().map(CapitalPosition::sourceEvidenceId).forEach(allEvidence::add);
        allEvidence = allEvidence.stream().distinct().sorted().toList();
        String hash = contentHash(contract, financialResult, lossClassification, allEvidence, positionsForHash, resultType, allocations);
        return new SyirkahCalculationSnapshot(
                calculationId,
                contract.contractId(),
                contract.versionNumber(),
                financialResult.periodStart(),
                financialResult.periodEnd(),
                financialResult.currencyCode(),
                financialResult.currencyScale(),
                net,
                resultType,
                lossCause,
                lossCause == LossCause.NOT_APPLICABLE ? null : lossClassification.decisionReference(),
                lossCause == LossCause.NOT_APPLICABLE ? null : lossClassification.classifiedBy(),
                lossCause == LossCause.NOT_APPLICABLE ? null : lossClassification.classifiedAt(),
                contract.lossPolicyId(),
                contract.lossPolicyVersion(),
                allocations,
                allEvidence,
                hash
        );
    }

    private static List<CapitalPosition> validateCapitalPositions(
            SyirkahContractVersion contract,
            FinancialResult result,
            List<CapitalPosition> capitalPositions,
            boolean required
    ) {
        List<CapitalPosition> supplied = List.copyOf(Objects.requireNonNull(capitalPositions, "capitalPositions must not be null"));
        if (!required && supplied.isEmpty()) return List.of();
        Map<String, CapitalPosition> byParty = new HashMap<>();
        Set<String> contractPartyIds = new HashSet<>();
        contract.parties().forEach(p -> contractPartyIds.add(p.partyId()));
        for (CapitalPosition position : supplied) {
            if (!contractPartyIds.contains(position.partyId())) throw new IllegalArgumentException("Capital position belongs to a non-contract party: " + position.partyId());
            if (!result.currencyCode().equals(position.currencyCode())) throw new IllegalArgumentException("Capital position currency mismatch for " + position.partyId());
            if (result.currencyScale() != position.currencyScale()) throw new IllegalArgumentException("Capital position precision policy mismatch for " + position.partyId());
            if (!result.periodEnd().equals(position.asOfDate())) throw new IllegalArgumentException("Capital position must be as of period end for " + position.partyId());
            if (byParty.put(position.partyId(), position) != null) throw new IllegalArgumentException("Duplicate capital position for " + position.partyId());
        }
        if (required && byParty.size() != contract.parties().size()) {
            throw new IllegalArgumentException("A verified capital position is required for every contract party for loss allocation");
        }
        return supplied.stream().sorted(Comparator.comparing(CapitalPosition::partyId)).toList();
    }

    private static List<WeightedParty> lossWeights(SyirkahContractVersion contract, List<CapitalPosition> positions) {
        Map<String, CapitalPosition> byParty = new HashMap<>();
        positions.forEach(p -> byParty.put(p.partyId(), p));
        List<WeightedParty> weights = new ArrayList<>();
        for (SyirkahPartyTerms party : contract.parties()) {
            CapitalPosition position = byParty.get(party.partyId());
            if (contract.lossAllocationMode() == LossAllocationMode.CAPITAL_PROVIDER_ONLY && !party.role().contributesCapitalByRole()) continue;
            BigInteger weight = position.amountAtRisk().movePointRight(position.currencyScale()).toBigIntegerExact();
            if (weight.signum() > 0) weights.add(new WeightedParty(party.partyId(), weight));
        }
        if (weights.isEmpty()) throw new IllegalStateException("No eligible positive capital-at-risk position for the selected loss policy");
        return List.copyOf(weights);
    }

    private static List<PartnerAllocation> allocate(BigDecimal amount, int scale, List<WeightedParty> parties) {
        BigInteger totalMinorUnits = amount.movePointRight(scale).toBigIntegerExact();
        BigInteger totalWeight = parties.stream().map(WeightedParty::weight).reduce(BigInteger.ZERO, BigInteger::add);
        if (totalWeight.signum() <= 0) throw new IllegalArgumentException("Allocation weights must sum to a positive value");

        List<Remainder> remainders = new ArrayList<>();
        BigInteger assigned = BigInteger.ZERO;
        for (WeightedParty party : parties) {
            BigInteger[] qr = totalMinorUnits.multiply(party.weight()).divideAndRemainder(totalWeight);
            assigned = assigned.add(qr[0]);
            remainders.add(new Remainder(party.partyId(), qr[0], qr[1]));
        }
        BigInteger remaining = totalMinorUnits.subtract(assigned);
        remainders.sort(Comparator.comparing(Remainder::remainder).reversed().thenComparing(Remainder::partyId));
        int leftover = remaining.intValueExact();
        Map<String, BigInteger> finalMinor = new HashMap<>();
        for (int i = 0; i < remainders.size(); i++) {
            Remainder r = remainders.get(i);
            finalMinor.put(r.partyId(), r.floorMinorUnits().add(i < leftover ? BigInteger.ONE : BigInteger.ZERO));
        }
        return finalMinor.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new PartnerAllocation(e.getKey(), new BigDecimal(e.getValue(), scale)))
                .toList();
    }

    private static String contentHash(
            SyirkahContractVersion contract,
            FinancialResult result,
            LossClassification lossClassification,
            List<String> evidence,
            List<CapitalPosition> capitalPositions,
            SyirkahCalculationSnapshot.ResultType resultType,
            List<PartnerAllocation> allocations
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                write(out, "NUMOU_SYIRKAH_CALCULATION_V1");
                write(out, contract.contractId());
                write(out, Integer.toString(contract.versionNumber()));
                write(out, contract.type().name());
                write(out, contract.effectiveFrom().toString());
                write(out, contract.effectiveUntil() == null ? "" : contract.effectiveUntil().toString());
                write(out, contract.currencyCode());
                write(out, Integer.toString(contract.currencyScale()));
                write(out, contract.lossAllocationMode().name());
                write(out, contract.lossPolicyId());
                write(out, contract.lossPolicyVersion());
                write(out, contract.policyApprovalReference());
                write(out, contract.agreementEvidenceId());
                write(out, contract.approvedBy());
                write(out, contract.approvedAt().toString());
                write(out, result.periodStart().toString());
                write(out, result.periodEnd().toString());
                write(out, result.currencyCode());
                write(out, Integer.toString(result.currencyScale()));
                write(out, result.revenue().toPlainString());
                write(out, result.directCosts().toPlainString());
                write(out, result.operatingCosts().toPlainString());
                write(out, result.otherCosts().toPlainString());
                write(out, result.netResult().toPlainString());
                write(out, lossClassification.cause().name());
                write(out, lossClassification.decisionReference() == null ? "" : lossClassification.decisionReference());
                write(out, lossClassification.classifiedBy() == null ? "" : lossClassification.classifiedBy());
                write(out, lossClassification.classifiedAt() == null ? "" : lossClassification.classifiedAt().toString());
                write(out, resultType.name());
                contract.parties().stream().sorted(Comparator.comparing(SyirkahPartyTerms::partyId)).forEach(p -> {
                    try {
                        write(out, p.partyId());
                        write(out, p.role().name());
                        write(out, Integer.toString(p.profitSharingRatio().basisPoints()));
                    } catch (IOException ex) { throw new HashingFailure(ex); }
                });
                capitalPositions.stream().sorted(Comparator.comparing(CapitalPosition::partyId)).forEach(p -> {
                    try {
                        write(out, p.partyId());
                        write(out, p.amountAtRisk().toPlainString());
                        write(out, p.currencyCode());
                        write(out, Integer.toString(p.currencyScale()));
                        write(out, p.asOfDate().toString());
                        write(out, p.sourceEvidenceId());
                    } catch (IOException ex) { throw new HashingFailure(ex); }
                });
                evidence.stream().sorted().distinct().forEach(e -> {
                    try { write(out, e); } catch (IOException ex) { throw new HashingFailure(ex); }
                });
                allocations.stream().sorted(Comparator.comparing(PartnerAllocation::partyId)).forEach(a -> {
                    try {
                        write(out, a.partyId());
                        write(out, a.amount().toPlainString());
                    } catch (IOException ex) { throw new HashingFailure(ex); }
                });
            }
            return java.util.HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException | IOException | HashingFailure ex) {
            throw new IllegalStateException("Unable to compute deterministic Syirkah content hash", ex);
        }
    }

    private static void write(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private record WeightedParty(String partyId, BigInteger weight) { }
    private record Remainder(String partyId, BigInteger floorMinorUnits, BigInteger remainder) { }
    private static final class HashingFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private HashingFailure(IOException cause) { super(cause); }
    }
}
