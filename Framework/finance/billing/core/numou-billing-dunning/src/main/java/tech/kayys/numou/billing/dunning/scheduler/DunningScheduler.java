package tech.kayys.numou.billing.dunning.scheduler;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.numou.billing.dunning.model.DunningCase;
import tech.kayys.numou.billing.dunning.service.DunningService;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class DunningScheduler {

    private static final Logger LOG = Logger.getLogger(DunningScheduler.class);

    @Inject
    DunningService dunningService;

    @Scheduled(every = "1h", delayed = "30s")
    void runDunningCycle() {
        LOG.info("Starting scheduled dunning execution cycle");
        List<DunningCase> cases = dunningService.listActiveCases();
        Instant now = Instant.now();

        int processed = 0;
        for (DunningCase dcase : cases) {
            if (dcase.getNextActionDate() != null && dcase.getNextActionDate().isBefore(now)) {
                dunningService.processCase(dcase);
                processed++;
            }
        }
        LOG.infof("Completed scheduled dunning cycle: processed %d cases", processed);
    }
}
