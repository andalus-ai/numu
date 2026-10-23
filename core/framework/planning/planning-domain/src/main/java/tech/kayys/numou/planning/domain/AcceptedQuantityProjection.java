package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.util.*;

/**
 * Deterministic interpretation of immutable quantity evidence and correction review decisions.
 * Only approved corrections affect the accepted measurement. Pending and rejected corrections
 * never replace the accepted value. Multiple approved successors are treated as a conflict.
 */
public record AcceptedQuantityProjection(
        UUID tenantId,
        UUID definitionId,
        UUID acceptedMeasurementId,
        BigDecimal acceptedQuantity,
        List<UUID> appliedCorrectionIds,
        List<UUID> pendingCorrectionIds,
        List<UUID> rejectedCorrectionIds) {

    public AcceptedQuantityProjection {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Definition id cannot be null");
        Objects.requireNonNull(acceptedMeasurementId, "Accepted measurement id cannot be null");
        Objects.requireNonNull(acceptedQuantity, "Accepted quantity cannot be null");
        appliedCorrectionIds = List.copyOf(appliedCorrectionIds);
        pendingCorrectionIds = List.copyOf(pendingCorrectionIds);
        rejectedCorrectionIds = List.copyOf(rejectedCorrectionIds);
    }

    public static AcceptedQuantityProjection resolve(
            UUID tenantId,
            UUID definitionId,
            List<QuantityMeasurement> measurements,
            List<QuantityMeasurementCorrection> corrections,
            List<QuantityCorrectionDecision> decisions) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Definition id cannot be null");
        Map<UUID, QuantityMeasurement> evidence = new HashMap<>();
        for (QuantityMeasurement measurement : measurements) {
            if (!tenantId.equals(measurement.tenantId()) || !definitionId.equals(measurement.definitionId())) {
                throw new IllegalArgumentException("Measurement history crosses tenant or definition boundary");
            }
            if (evidence.put(measurement.id(), measurement) != null) {
                throw new IllegalArgumentException("Duplicate measurement ID in history");
            }
        }
        if (evidence.isEmpty()) throw new NoSuchElementException("No quantity measurements exist for this definition");

        Map<UUID, QuantityMeasurementCorrection> correctionById = new HashMap<>();
        Map<UUID, List<QuantityMeasurementCorrection>> outgoing = new HashMap<>();
        for (QuantityMeasurementCorrection correction : corrections) {
            if (!tenantId.equals(correction.tenantId()) || !definitionId.equals(correction.definitionId())) {
                throw new IllegalArgumentException("Correction history crosses tenant or definition boundary");
            }
            if (!evidence.containsKey(correction.originalMeasurementId())
                    || !evidence.containsKey(correction.replacementMeasurementId())) {
                throw new IllegalArgumentException("Correction references missing measurement evidence");
            }
            if (correctionById.put(correction.id(), correction) != null) {
                throw new IllegalArgumentException("Duplicate correction ID in history");
            }
            outgoing.computeIfAbsent(correction.originalMeasurementId(), ignored -> new ArrayList<>()).add(correction);
        }

        Map<UUID, QuantityCorrectionDecision> latestDecision = new HashMap<>();
        for (QuantityCorrectionDecision decision : decisions) {
            if (!tenantId.equals(decision.tenantId()) || !definitionId.equals(decision.definitionId())) {
                throw new IllegalArgumentException("Review history crosses tenant or definition boundary");
            }
            if (!correctionById.containsKey(decision.correctionId())) {
                throw new IllegalArgumentException("Decision references an unknown correction");
            }
            QuantityCorrectionDecision previous = latestDecision.get(decision.correctionId());
            if (previous == null || decision.decidedAt().isAfter(previous.decidedAt())
                    || (decision.decidedAt().equals(previous.decidedAt())
                    && decision.id().toString().compareTo(previous.id().toString()) > 0)) {
                latestDecision.put(decision.correctionId(), decision);
            }
        }

        List<UUID> pending = new ArrayList<>();
        List<UUID> rejected = new ArrayList<>();
        List<UUID> applied = new ArrayList<>();
        for (QuantityMeasurementCorrection correction : corrections) {
            QuantityCorrectionDecision decision = latestDecision.get(correction.id());
            if (decision == null) pending.add(correction.id());
            else if (decision.decision() == QuantityCorrectionDecision.Decision.REJECTED) rejected.add(correction.id());
        }
        Comparator<UUID> idOrder = Comparator.comparing(UUID::toString);
        pending.sort(idOrder);
        rejected.sort(idOrder);

        // Corrections are directed edges. Starting at the latest uncorrected evidence can be
        // ambiguous, so resolve from root measurements and require one approved edge per step.
        Set<UUID> replacementIds = new HashSet<>();
        for (QuantityMeasurementCorrection correction : corrections) {
            QuantityCorrectionDecision decision = latestDecision.get(correction.id());
            if (decision != null && decision.decision() == QuantityCorrectionDecision.Decision.APPROVED) {
                replacementIds.add(correction.replacementMeasurementId());
            }
        }
        List<UUID> roots = evidence.keySet().stream().filter(id -> !replacementIds.contains(id)).sorted(idOrder).toList();
        if (roots.size() != 1) {
            throw new IllegalStateException("Cannot determine a unique quantity correction chain: expected one root, found " + roots.size());
        }
        UUID current = roots.get(0);
        Set<UUID> visited = new HashSet<>();
        while (visited.add(current)) {
            List<QuantityMeasurementCorrection> approved = outgoing.getOrDefault(current, List.of()).stream()
                    .filter(c -> {
                        QuantityCorrectionDecision d = latestDecision.get(c.id());
                        return d != null && d.decision() == QuantityCorrectionDecision.Decision.APPROVED;
                    }).toList();
            if (approved.size() > 1) throw new IllegalStateException("Conflicting approved corrections branch from measurement " + current);
            if (approved.isEmpty()) break;
            QuantityMeasurementCorrection next = approved.get(0);
            if (visited.contains(next.replacementMeasurementId())) {
                throw new IllegalStateException("Cycle detected in quantity correction history");
            }
            applied.add(next.id());
            current = next.replacementMeasurementId();
        }
        if (visited.size() < evidence.size()) {
            // Multiple independent measurements are normal observations, not corrections.
            // Choose the latest uncorrected observation only when it is not involved in a correction chain.
            List<QuantityMeasurement> ordinary = evidence.values().stream()
                    .filter(m -> outgoing.getOrDefault(m.id(), List.of()).isEmpty())
                    .filter(m -> !replacementIds.contains(m.id()))
                    .sorted(Comparator.comparing(QuantityMeasurement::asOfDate)
                            .thenComparing(QuantityMeasurement::recordedAt)
                            .thenComparing(m -> m.id().toString()))
                    .toList();
            if (!ordinary.isEmpty()) {
                QuantityMeasurement latest = ordinary.get(ordinary.size() - 1);
                if (applied.isEmpty() || latest.asOfDate().isAfter(evidence.get(current).asOfDate())) current = latest.id();
            }
        }
        QuantityMeasurement accepted = evidence.get(current);
        return new AcceptedQuantityProjection(tenantId, definitionId, accepted.id(), accepted.actualQuantity(),
                applied, pending, rejected);
    }
}
