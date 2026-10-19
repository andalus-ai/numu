package tech.kayys.tax.startup;

import tech.kayys.tax.service.TaxConfigurationService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class TaxApplicationStartup {
    
    @Inject
    TaxConfigurationService taxConfigurationService;
    
    void onStart(@Observes StartupEvent ev) {
        // Initialize default tax configurations
        taxConfigurationService.initializeDefaultConfiguration();
    }
}
