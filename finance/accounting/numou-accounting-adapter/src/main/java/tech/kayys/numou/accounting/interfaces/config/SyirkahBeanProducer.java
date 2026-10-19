package tech.kayys.numou.accounting.interfaces.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.accounting.application.islamic.SyirkahApplicationService;
import tech.kayys.numou.accounting.application.port.SyirkahRepository;
import tech.kayys.numou.accounting.domain.islamic.SyirkahCalculator;

/** CDI wiring keeps framework annotations out of the accounting domain module. */
@ApplicationScoped
public class SyirkahBeanProducer {
    @Produces
    @ApplicationScoped
    public SyirkahCalculator syirkahCalculator() {
        return new SyirkahCalculator();
    }

    @Produces
    @ApplicationScoped
    public SyirkahApplicationService syirkahApplicationService(
            SyirkahRepository repository, SyirkahCalculator calculator) {
        return new SyirkahApplicationService(repository, calculator);
    }
}
