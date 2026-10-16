package tech.kayys.syirkah.accounting.application.sdk.plugin.aaoifi;

import tech.kayys.syirkah.accounting.application.compliance.ComplianceRule;
import tech.kayys.syirkah.accounting.application.sdk.plugin.CompliancePack;
import tech.kayys.syirkah.accounting.application.sdk.plugin.PluginContext;
import tech.kayys.syirkah.accounting.application.sdk.plugin.PluginMetadata;
import tech.kayys.syirkah.accounting.domain.compliance.ComplianceConfiguration;
import tech.kayys.syirkah.accounting.domain.islamic.ShariaContractType;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;

/**
 * AAOIFI Lite compliance pack.
 */
public final class AaoifiLitePlugin implements CompliancePack {

    private ZakatProjection zakatProjection;

    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
                "aaoifi-lite",
                "AAOIFI Lite",
                "1.0.0",
                "compliance",
                "Kayys Platform",
                "AAOIFI FAS 2/9 Murabahah profit recognition and Zakat reporting"
        );
    }

    @Override
    public String jurisdiction() { return "ar-SA"; }

    @Override
    public String standardLabel() { return "AAOIFI"; }

    @Override
    public void initialize(PluginContext context) {
        // Compliance rule: Murabahah must be properly tagged if Sharia is enabled
        context.registerComplianceRule(new MurabahaMarginRule());

        // Zakat projection for AAOIFI FAS 9
        zakatProjection = new ZakatProjection();
        context.registerProjection(zakatProjection);
    }

    /** Exposes the Zakat projection after initialization. */
    public ZakatProjection zakatProjection() {
        return zakatProjection;
    }

    private static final class MurabahaMarginRule implements ComplianceRule {
        @Override
        public String name() { return "AAOIFI-FAS2-MurabahaMarginRule"; }

        @Override
        public void evaluate(JournalEntry entry, ComplianceConfiguration config) {
            if (config.enforceShariaContracts() && entry.getShariaContractType() == ShariaContractType.MURABAHAH) {
                // Murabahah contract validation
                if (entry.getLines().isEmpty()) {
                    throw new IllegalStateException("Murabahah journal entry must have valid line items");
                }
            }
        }
    }
}
