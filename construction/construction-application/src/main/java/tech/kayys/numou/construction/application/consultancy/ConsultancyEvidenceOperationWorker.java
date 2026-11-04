package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.construction.application.consultancy.ConsultancyEvidenceOperationStore.ClaimedConsultancyEvidenceOperation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tenant-scoped batch runner for durable consultancy evidence operations.
 * Tenant enumeration and scheduling are intentionally owned by the deployment.
 */
@ApplicationScoped
public class ConsultancyEvidenceOperationWorker {
    private final ConsultancyEvidenceOperationStore operations;
    private final ConsultancyEvidenceOperationProcessor processor;
    private final Clock clock;

    @Inject
    public ConsultancyEvidenceOperationWorker(ConsultancyEvidenceOperationStore operations,
            ConsultancyEvidenceOperationProcessor processor) {
        this(operations, processor, Clock.systemUTC());
    }

    ConsultancyEvidenceOperationWorker(ConsultancyEvidenceOperationStore operations,
            ConsultancyEvidenceOperationProcessor processor, Clock clock) {
        this.operations = Objects.requireNonNull(operations);
        this.processor = Objects.requireNonNull(processor);
        this.clock = Objects.requireNonNull(clock);
    }

    public Uni<BatchResult> runTenantBatch(String tenantId, String workerId, WorkerSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
        Instant claimedAt = clock.instant();
        return operations.claimDue(tenantId, claimedAt, claimedAt.plus(settings.leaseDuration()),
                        settings.batchSize(), workerId)
                .chain(claims -> processClaims(claims, settings)
                        .map(outcomes -> summarize(claims.size(), outcomes)));
    }

    private Uni<List<Boolean>> processClaims(
            List<ClaimedConsultancyEvidenceOperation> claims, WorkerSettings settings) {
        if (claims.isEmpty()) return Uni.createFrom().item(List.of());
        return Multi.createFrom().iterable(claims)
                .onItem().transformToUniAndMerge(claim -> processWithLeaseRenewal(claim, settings))
                .withConcurrency(settings.maxConcurrency())
                .collect().asList();
    }

    private Uni<Boolean> processWithLeaseRenewal(
            ClaimedConsultancyEvidenceOperation initialClaim, WorkerSettings settings) {
        AtomicBoolean processingFinished = new AtomicBoolean();
        Uni<Boolean> processing = processor.process(initialClaim, settings.maxAttempts())
                .onTermination().invoke(() -> processingFinished.set(true));
        Uni<Void> heartbeat = renewUntilFinished(initialClaim, settings, processingFinished);
        return Uni.combine().any().of(processing, heartbeat).map(Boolean.class::cast);
    }

    private Uni<Void> renewUntilFinished(ClaimedConsultancyEvidenceOperation claim,
            WorkerSettings settings, AtomicBoolean processingFinished) {
        if (processingFinished.get()) return Uni.createFrom().nothing();
        return Uni.createFrom().voidItem().onItem().delayIt().by(settings.leaseRenewalInterval())
                .chain(() -> {
                    if (processingFinished.get()) return Uni.createFrom().nothing();
                    Instant now = clock.instant();
                    return operations.renewLease(claim, now, now.plus(settings.leaseDuration()))
                            .chain(renewed -> {
                                if (renewed.isEmpty()) {
                                    return Uni.createFrom().failure(
                                            new ConsultancyEvidenceLeaseLostException(claim.operation().operationId()));
                                }
                                return renewUntilFinished(renewed.get(), settings, processingFinished);
                            });
                });
    }

    private static BatchResult summarize(int claimed, List<Boolean> outcomes) {
        int completed = (int) outcomes.stream().filter(Boolean.TRUE::equals).count();
        return new BatchResult(claimed, completed, claimed - completed);
    }

    public record WorkerSettings(int batchSize, int maxConcurrency, int maxAttempts,
            Duration leaseDuration, Duration leaseRenewalInterval) {
        public WorkerSettings {
            if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("batchSize must be between 1 and 500");
            if (maxConcurrency < 1 || maxConcurrency > 128) throw new IllegalArgumentException("maxConcurrency must be between 1 and 128");
            if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
            Objects.requireNonNull(leaseDuration, "leaseDuration");
            Objects.requireNonNull(leaseRenewalInterval, "leaseRenewalInterval");
            if (leaseRenewalInterval.isZero() || leaseRenewalInterval.isNegative()) {
                throw new IllegalArgumentException("leaseRenewalInterval must be positive");
            }
            if (leaseDuration.compareTo(leaseRenewalInterval.multipliedBy(3)) < 0) {
                throw new IllegalArgumentException("leaseDuration must be at least three times leaseRenewalInterval");
            }
        }
    }

    public record BatchResult(int claimed, int completed, int notCompleted) {
        public BatchResult {
            if (claimed < 0 || completed < 0 || notCompleted < 0 || completed + notCompleted != claimed) {
                throw new IllegalArgumentException("Inconsistent evidence worker batch counts");
            }
        }
    }

    public static final class ConsultancyEvidenceLeaseLostException extends RuntimeException {
        public ConsultancyEvidenceLeaseLostException(java.util.UUID operationId) {
            super("Lease lost while processing consultancy evidence operation " + operationId);
        }
    }
}