package tech.kayys.numou.accounting.application.sdk.plugin;

import tech.kayys.numou.accounting.application.compliance.ComplianceRule;
import tech.kayys.numou.accounting.application.projection.Projection;
import tech.kayys.numou.accounting.domain.event.AccountingEvent;

/**
 * Context passed to {@link PlatformPlugin#initialize(PluginContext)} during
 * platform startup.
 */
public interface PluginContext {

    /** Add a compliance / validation rule to the global pipeline. */
    void registerComplianceRule(ComplianceRule rule);

    /** Register an event projection contributed by this plugin. */
    void registerProjection(Projection<? extends AccountingEvent> projection);
}
