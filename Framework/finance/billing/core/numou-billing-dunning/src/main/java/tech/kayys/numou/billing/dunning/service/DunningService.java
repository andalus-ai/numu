package tech.kayys.numou.billing.dunning.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.numou.billing.application.port.NotificationPort;
import tech.kayys.numou.billing.domain.valueobject.DunningAction;
import tech.kayys.numou.billing.domain.valueobject.DunningLevel;
import tech.kayys.numou.billing.dunning.model.DunningCase;
import tech.kayys.numou.billing.dunning.model.DunningPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DunningService {

    private static final Logger LOG = Logger.getLogger(DunningService.class);

    @Inject
    NotificationPort notificationPort;

    private final ConcurrentHashMap<String, DunningCase> activeCases = new ConcurrentHashMap<>();
    private final DunningPolicy policy = DunningPolicy.defaultPolicy();

    public DunningCase openCase(String scheduleId, String customerId, String invoiceId, BigDecimal amount, String currency) {
        DunningCase dcase = new DunningCase(scheduleId, customerId, invoiceId, amount, currency);
        activeCases.put(dcase.getCaseId(), dcase);
        LOG.infof("Opened dunning case %s for customer %s on invoice %s", dcase.getCaseId(), customerId, invoiceId);
        return dcase;
    }

    public void processCase(DunningCase dcase) {
        if (dcase.getStatus() != DunningCase.CaseStatus.ACTIVE) {
            return;
        }

        int stepIndex = dcase.getAttemptCount();
        if (stepIndex >= policy.steps().size()) {
            LOG.warnf("Dunning case %s exceeded maximum policy steps. Escalating to collections.", dcase.getCaseId());
            dcase.advanceLevel(DunningLevel.create(5, "Collections Referral", 0, DunningAction.COLLECTIONS_REFERRAL, "collections"), null);
            return;
        }

        DunningPolicy.DunningStep step = policy.steps().get(stepIndex);
        LOG.infof("Executing step %d (%s) for dunning case %s", step.stepNumber(), step.action(), dcase.getCaseId());

        if (notificationPort != null) {
            notificationPort.sendBillingReminder(
                dcase.getCustomerId(),
                dcase.getOverdueAmount().toString(),
                Instant.now().toString()
            );
        }

        Instant nextDate = Instant.now().plus(step.waitBeforeNextStep());
        dcase.advanceLevel(step.level(), nextDate);
    }

    public void resolveCase(String caseId, String notes) {
        DunningCase dcase = activeCases.get(caseId);
        if (dcase != null) {
            dcase.resolve(notes);
            LOG.infof("Resolved dunning case %s: %s", caseId, notes);
        }
    }

    public List<DunningCase> listActiveCases() {
        return new ArrayList<>(activeCases.values());
    }

    public Optional<DunningCase> getCase(String caseId) {
        return Optional.ofNullable(activeCases.get(caseId));
    }
}
