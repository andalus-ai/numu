package tech.kayys.syirkah.accounting.application.risk;

import tech.kayys.syirkah.accounting.domain.risk.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RiskService {

    private final Map<String, RiskItem> risks = new ConcurrentHashMap<>();
    private final Map<String, ControlAssessment> assessments = new ConcurrentHashMap<>();
    private final Map<String, RiskIncident> incidents = new ConcurrentHashMap<>();

    // ── Risk Registration ─────────────────────────────────────────────────────

    public RiskItem registerRisk(String riskId, String category, String title,
                                 RiskItem.Level inherentRisk) {
        var risk = new RiskItem(riskId, category, title, inherentRisk);
        risks.put(riskId, risk);
        return risk;
    }

    public RiskItem registerRisk(String riskId, String title, RiskItem.Level inherentRisk) {
        return registerRisk(riskId, "OPERATIONAL", title, inherentRisk);
    }

    public RiskHeatMap.ScoreResult scoreRisk(int likelihood, int impact) {
        return RiskHeatMap.evaluate(likelihood, impact);
    }

    public RiskItem getRisk(String riskId) {
        var r = risks.get(riskId);
        if (r == null) throw new IllegalArgumentException("RiskItem not found: " + riskId);
        return r;
    }

    public List<RiskItem> listRisks() {
        return List.copyOf(risks.values());
    }

    // ── Control Assessment ────────────────────────────────────────────────────

    public ControlAssessment assessControl(String riskId, String controlId,
                                           String controlName, boolean effective) {
        var risk = getRisk(riskId);
        var assessment = new ControlAssessment(controlId, riskId, controlName, effective, LocalDate.now());
        assessments.put(controlId, assessment);

        if (effective) {
            RiskItem.Level reduced = switch (risk.residualRisk()) {
                case CRITICAL -> RiskItem.Level.HIGH;
                case HIGH     -> RiskItem.Level.MEDIUM;
                default       -> RiskItem.Level.LOW;
            };
            risk.updateResidualRisk(reduced);
        }
        return assessment;
    }

    public List<ControlAssessment> listAssessments() {
        return List.copyOf(assessments.values());
    }

    // ── Incident Management ───────────────────────────────────────────────────

    public RiskIncident recordIncident(String riskId, String title, String description,
                                       BigDecimal financialLoss, String reportedBy) {
        getRisk(riskId); // validate risk exists
        var id = RiskIncidentId.newId();
        var inc = new RiskIncident(id, riskId, title, description, financialLoss, reportedBy);
        incidents.put(id.value(), inc);
        return inc;
    }

    public List<RiskIncident> getIncidents(String riskId) {
        return incidents.values().stream()
                .filter(i -> i.riskId().equals(riskId))
                .toList();
    }
}
