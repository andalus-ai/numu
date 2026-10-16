package tech.kayys.syirkah.project.application.risk.policy;

import tech.kayys.syirkah.project.domain.risk.IssuePriority;
import tech.kayys.syirkah.project.domain.risk.IssueSeverity;
import tech.kayys.syirkah.project.domain.risk.RiskScore;
import tech.kayys.syirkah.project.domain.risk.event.RiskMaterialized;

import java.util.UUID;

/**
 * Default materialization: severity/priority are derived from the
 * calculated risk score, the issue number derives from the risk
 * number (a risk can materialize only once), and the risk's title and
 * description seed the issue. No owner is assigned — that is a human
 * decision made once the issue is open.
 */
public final class DefaultRiskMaterializationPolicy
        implements RiskMaterializationPolicy {

    @Override
    public IssueDraft createIssue(RiskMaterialized event) {
        var score = event.score();

        return new IssueDraft(
                event.projectId(),
                event.number() + "-ISSUE",
                "Risk materialized: " + event.title(),
                event.description(),
                severityOf(score),
                priorityOf(score),
                null
        );
    }

    private IssueSeverity severityOf(RiskScore score) {
        if (score.isCritical()) {
            return IssueSeverity.CRITICAL;
        }
        if (score.isHigh()) {
            return IssueSeverity.HIGH;
        }
        if (score.isMedium()) {
            return IssueSeverity.MEDIUM;
        }
        return IssueSeverity.LOW;
    }

    private IssuePriority priorityOf(RiskScore score) {
        if (score.isCritical()) {
            return IssuePriority.URGENT;
        }
        if (score.isHigh()) {
            return IssuePriority.HIGH;
        }
        if (score.isMedium()) {
            return IssuePriority.MEDIUM;
        }
        return IssuePriority.LOW;
    }
}
