package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.util.*;

/** Deterministic view over immutable measurements and append-only correction review decisions. */
public record AcceptedQuantityProjection(UUID tenantId, UUID definitionId, UUID acceptedMeasurementId,
        BigDecimal acceptedQuantity, List<UUID> appliedCorrectionIds, List<UUID> pendingCorrectionIds,
        List<UUID> rejectedCorrectionIds) {
    public AcceptedQuantityProjection {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(definitionId);
        Objects.requireNonNull(acceptedMeasurementId); Objects.requireNonNull(acceptedQuantity);
        appliedCorrectionIds = List.copyOf(appliedCorrectionIds);
        pendingCorrectionIds = List.copyOf(pendingCorrectionIds);
        rejectedCorrectionIds = List.copyOf(rejectedCorrectionIds);
    }

    public static AcceptedQuantityProjection resolve(UUID tenantId, UUID definitionId,
            List<QuantityMeasurement> measurements, List<QuantityMeasurementCorrection> corrections,
            List<QuantityCorrectionDecision> decisions) {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(definitionId);
        Map<UUID, QuantityMeasurement> evidence = new HashMap<>();
        for (var m : measurements) {
            if (!tenantId.equals(m.tenantId()) || !definitionId.equals(m.definitionId()))
                throw new IllegalArgumentException("Measurement history crosses tenant or definition boundary");
            if (evidence.put(m.id(), m) != null) throw new IllegalArgumentException("Duplicate measurement ID");
        }
        if (evidence.isEmpty()) throw new NoSuchElementException("No quantity measurements exist for this definition");
        Map<UUID, QuantityMeasurementCorrection> byId = new HashMap<>();
        Map<UUID, List<QuantityMeasurementCorrection>> outgoing = new HashMap<>();
        Set<UUID> everyReplacement = new HashSet<>();
        for (var c : corrections) {
            if (!tenantId.equals(c.tenantId()) || !definitionId.equals(c.definitionId()))
                throw new IllegalArgumentException("Correction history crosses tenant or definition boundary");
            if (!evidence.containsKey(c.originalMeasurementId()) || !evidence.containsKey(c.replacementMeasurementId()))
                throw new IllegalArgumentException("Correction references missing measurement evidence");
            if (byId.put(c.id(), c) != null) throw new IllegalArgumentException("Duplicate correction ID");
            outgoing.computeIfAbsent(c.originalMeasurementId(), ignored -> new ArrayList<>()).add(c);
            everyReplacement.add(c.replacementMeasurementId());
        }
        Map<UUID, QuantityCorrectionDecision> latest = new HashMap<>();
        for (var d : decisions) {
            if (!tenantId.equals(d.tenantId()) || !definitionId.equals(d.definitionId()))
                throw new IllegalArgumentException("Review history crosses tenant or definition boundary");
            if (!byId.containsKey(d.correctionId())) throw new IllegalArgumentException("Decision references unknown correction");
            var prior = latest.get(d.correctionId());
            if (prior == null || d.decidedAt().isAfter(prior.decidedAt())
                    || (d.decidedAt().equals(prior.decidedAt()) && d.id().toString().compareTo(prior.id().toString()) > 0))
                latest.put(d.correctionId(), d);
        }
        List<UUID> pending = new ArrayList<>(), rejected = new ArrayList<>(), applied = new ArrayList<>();
        for (var c : corrections) {
            var d = latest.get(c.id());
            if (d == null) pending.add(c.id());
            else if (d.decision() == QuantityCorrectionDecision.Decision.REJECTED) rejected.add(c.id());
        }
        Comparator<UUID> idOrder = Comparator.comparing(UUID::toString);
        pending.sort(idOrder); rejected.sort(idOrder);
        List<QuantityMeasurement> roots = evidence.values().stream().filter(m -> !everyReplacement.contains(m.id()))
                .sorted(Comparator.comparing(QuantityMeasurement::asOfDate)
                        .thenComparing(QuantityMeasurement::recordedAt).thenComparing(m -> m.id().toString())).toList();
        if (roots.isEmpty()) throw new IllegalStateException("Correction graph has no root measurement");
        UUID current = roots.get(roots.size() - 1).id();
        Set<UUID> visited = new HashSet<>();
        while (visited.add(current)) {
            List<QuantityMeasurementCorrection> approved = outgoing.getOrDefault(current, List.of()).stream()
                    .filter(c -> { var d = latest.get(c.id()); return d != null && d.decision() == QuantityCorrectionDecision.Decision.APPROVED; })
                    .toList();
            if (approved.size() > 1) throw new IllegalStateException("Conflicting approved corrections branch from measurement " + current);
            if (approved.isEmpty()) break;
            var next = approved.get(0);
            if (visited.contains(next.replacementMeasurementId())) throw new IllegalStateException("Cycle in correction history");
            applied.add(next.id()); current = next.replacementMeasurementId();
        }
        var accepted = evidence.get(current);
        return new AcceptedQuantityProjection(tenantId, definitionId, accepted.id(), accepted.actualQuantity(),
                applied, pending, rejected);
    }
}
