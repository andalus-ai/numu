package tech.kayys.syirkah.accounting.application.quality;

import tech.kayys.syirkah.accounting.domain.quality.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class QualityService {

    private final Map<InspectionId, Inspection> inspections = new ConcurrentHashMap<>();
    private final Map<String, NonConformance> nonConformances = new ConcurrentHashMap<>();
    private final Map<String, CapaAction> capas = new ConcurrentHashMap<>();
    private final Map<String, Specification> specifications = new ConcurrentHashMap<>();
    private final Map<String, SampleLot> sampleLots = new ConcurrentHashMap<>();
    private final InspectionEvaluationEngine evalEngine = new InspectionEvaluationEngine();

    // ── Specifications ────────────────────────────────────────────────────────

    public Specification createSpecification(String specId, String itemCode, String name, int version) {
        var spec = new Specification(SpecificationId.of(specId), itemCode, name, version);
        specifications.put(specId, spec);
        return spec;
    }

    public Specification getSpecification(String specId) {
        var spec = specifications.get(specId);
        if (spec == null) throw new IllegalArgumentException("Specification not found: " + specId);
        return spec;
    }

    // ── Sample Lots ───────────────────────────────────────────────────────────

    public SampleLot createSampleLot(String lotId, String itemCode, String batchNumber, int sampleSize) {
        var lot = new SampleLot(SampleLotId.of(lotId), itemCode, batchNumber, sampleSize);
        sampleLots.put(lotId, lot);
        return lot;
    }

    public SampleLot getSampleLot(String lotId) {
        var lot = sampleLots.get(lotId);
        if (lot == null) throw new IllegalArgumentException("SampleLot not found: " + lotId);
        return lot;
    }

    // ── Inspections ───────────────────────────────────────────────────────────

    public Inspection createInspection(String itemReference, String inspector) {
        var id = new InspectionId(UUID.randomUUID().toString());
        var insp = new Inspection(id, itemReference, inspector);
        inspections.put(id, insp);
        return insp;
    }

    public void passInspection(InspectionId id, String notes) {
        getInspection(id).pass(notes != null ? notes : "");
    }

    public void passInspection(InspectionId id) {
        passInspection(id, "");
    }

    public void failInspection(InspectionId id, String notes) {
        getInspection(id).fail(notes != null ? notes : "");
    }

    public void failInspection(InspectionId id) {
        failInspection(id, "");
    }

    public Inspection getInspection(InspectionId id) {
        var insp = inspections.get(id);
        if (insp == null) throw new IllegalArgumentException("Inspection not found: " + id.value());
        return insp;
    }

    public InspectionEvaluationEngine.EvaluationResult evaluateAgainstSpec(InspectionId id, String specId, Map<String, Double> measured) {
        var insp = getInspection(id);
        var spec = getSpecification(specId);
        var result = evalEngine.evaluate(spec, measured);
        if (result.passed()) {
            insp.pass(result.remarks());
        } else {
            insp.fail(result.remarks());
        }
        return result;
    }

    // ── NCR ───────────────────────────────────────────────────────────────────

    public NonConformance recordNcr(InspectionId inspectionId, String title,
                                    NonConformance.Severity severity) {
        var insp = getInspection(inspectionId);
        if (insp.status() != Inspection.Status.FAILED) {
            throw new IllegalStateException(
                    "NCR can only be raised against a FAILED inspection, current: " + insp.status());
        }
        var ncr = new NonConformance(UUID.randomUUID().toString(), title, severity);
        nonConformances.put(ncr.id(), ncr);
        return ncr;
    }

    public void dispositionNcr(String ncrId, DispositionType type, String approvedBy, String justification, BigDecimal costImpact) {
        var ncr = getNcr(ncrId);
        ncr.assignDisposition(type, approvedBy, justification, costImpact);
    }

    public Optional<NonConformance> findNcr(String id) {
        return Optional.ofNullable(nonConformances.get(id));
    }

    public NonConformance getNcr(String id) {
        var ncr = nonConformances.get(id);
        if (ncr == null) throw new IllegalArgumentException("NCR not found: " + id);
        return ncr;
    }

    public NonConformance getNcr(Inspection insp, String id) {
        return getNcr(id);
    }

    // ── CAPA ─────────────────────────────────────────────────────────────────

    public CapaAction createCapa(NonConformance ncr, String actionPlan, String owner) {
        return createCapa(ncr.title(), actionPlan, owner, LocalDate.now().plusMonths(1));
    }

    public CapaAction createCapa(String rootCause, String actionPlan,
                                 String owner, LocalDate targetDate) {
        var capa = new CapaAction(UUID.randomUUID().toString(), rootCause, actionPlan, owner, targetDate);
        capas.put(capa.id(), capa);
        return capa;
    }

    public Optional<CapaAction> findCapa(String id) {
        return Optional.ofNullable(capas.get(id));
    }
}
