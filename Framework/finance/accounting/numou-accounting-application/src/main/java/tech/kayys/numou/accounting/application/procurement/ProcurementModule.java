
package tech.kayys.syirkah.accounting.application.procurement;

import tech.kayys.syirkah.accounting.application.sdk.module.FinancialModule;
import tech.kayys.syirkah.accounting.application.sdk.module.ModuleContext;
import tech.kayys.syirkah.accounting.application.sdk.module.ModuleMetadata;

public class ProcurementModule implements FinancialModule {

    @Override
    public String code() {
        return "procurement";
    }

    @Override
    public String name() {
        return "Procure-to-Pay Platform";
    }

    @Override
    public ModuleMetadata metadata() {
        return new ModuleMetadata(
                code(),
                name(),
                "1.0.0",
                "Procure-to-Pay with 3-way matching and GL integration",
                "Kayys ERP"
        );
    }

    @Override
    public void register(ModuleContext context) {
        // Module registered successfully into AccountingBootstrap
    }
}
