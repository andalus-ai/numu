package tech.kayys.syirkah.accounting.domain.audit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class AuditPlanBuilder {

    public void build(AuditPlan plan,
                      List<AuditableEntity> universe,
                      int hoursBudget,
                      String leadAuditor) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(universe, "universe");
        if (hoursBudget < 0) throw new IllegalArgumentException("hoursBudget must be >= 0");

        List<AuditableEntity> ranked = new ArrayList<>(universe);
        ranked.sort(Comparator
                .comparingInt((AuditableEntity e) -> e.rating().ordinal()).reversed()
                .thenComparingInt(AuditableEntity::riskScore).reversed());

        int remaining = hoursBudget;
        LocalDate cursor = LocalDate.of(plan.planYear(), 1, 15);
        for (AuditableEntity e : ranked) {
            if (remaining <= 0) break;
            int planned = Math.min(hoursFor(e.rating()), remaining);
            remaining -= planned;
            plan.addItem(new AuditPlanItem(
                    e.id(),
                    cursor,
                    cursor.plusDays(planned > 40 ? 20 : 10),
                    leadAuditor,
                    planned,
                    "Risk rating " + e.rating() + ", score " + e.riskScore()));
            cursor = cursor.plusDays(planned > 40 ? 30 : 15);
        }
    }

    private static int hoursFor(RiskRating rating) {
        return switch (rating) {
            case CRITICAL -> 80;
            case HIGH -> 60;
            case MEDIUM -> 40;
            case LOW -> 20;
        };
    }
}
