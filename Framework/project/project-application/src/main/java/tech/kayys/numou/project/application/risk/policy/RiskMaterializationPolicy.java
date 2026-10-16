package tech.kayys.syirkah.project.application.risk.policy;

import tech.kayys.syirkah.project.domain.risk.event.RiskMaterialized;

/**
 * Decides how a materialized risk becomes an issue.
 *
 * The Risk aggregate itself never constructs an Issue — that would
 * couple two aggregates. The application reacts to the
 * {@code RiskMaterialized} event through this policy instead.
 */
public interface RiskMaterializationPolicy {

    IssueDraft createIssue(RiskMaterialized event);
}
