package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallation;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationTransition;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Atomic persistence boundary for installation creation.
 * Implementations must commit the tenant/app uniqueness claim, hashed idempotency key,
 * installation aggregate and transition outbox record in one transaction.
 */
@FunctionalInterface
public interface ApplicationInstallationStore {
    CompletionStage<CreateResult> createIfAbsent(ApplicationInstallation installation,
            String idempotencyKeySha256, ApplicationInstallationTransition requestedEvent);

    record CreateResult(Outcome outcome, Optional<ApplicationInstallation> installation) {
        public enum Outcome { CREATED, REPLAYED, CONFLICT }
        public CreateResult {
            if (outcome == null || installation == null) throw new IllegalArgumentException("outcome and installation are required");
            if (outcome != Outcome.CONFLICT && installation.isEmpty())
                throw new IllegalArgumentException("created/replayed result must include installation");
        }
    }
}
