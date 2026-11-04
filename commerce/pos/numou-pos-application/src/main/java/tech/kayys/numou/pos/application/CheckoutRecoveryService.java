package tech.kayys.numou.pos.application;

import tech.kayys.numou.pos.application.port.out.CheckoutRepository;
import tech.kayys.numou.pos.domain.CheckoutSession;

import java.util.Objects;

/** Bounded tenant-scoped recovery entry point for a scheduler or operator workflow. */
public final class CheckoutRecoveryService {
    private final CheckoutRepository repository;
    private final CheckoutOrchestrationService orchestration;

    public CheckoutRecoveryService(CheckoutRepository repository, CheckoutOrchestrationService orchestration) {
        this.repository = Objects.requireNonNull(repository);
        this.orchestration = Objects.requireNonNull(orchestration);
    }

    public RecoveryReport recoverTenant(String tenantId, int limit) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        var candidates = repository.findRecoverable(tenantId.trim(), limit);
        int completed = 0, cancelled = 0, pending = 0, errors = 0;
        for (CheckoutSession candidate : candidates) {
            try {
                CheckoutSession result = orchestration.reconcile(tenantId, candidate.id());
                switch (result.status()) {
                    case COMPLETED -> completed++;
                    case CANCELLED -> cancelled++;
                    default -> pending++;
                }
            } catch (RuntimeException failure) {
                errors++;
            }
        }
        return new RecoveryReport(candidates.size(), completed, cancelled, pending, errors);
    }

    public record RecoveryReport(int attempted, int completed, int cancelled, int pending, int errors) { }
}
