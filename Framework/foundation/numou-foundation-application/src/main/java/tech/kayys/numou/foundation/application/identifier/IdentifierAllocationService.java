package tech.kayys.numou.foundation.application.identifier;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.identifier.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Application service for policy resolution, sequence allocation, and identifier formatting (config03.md §P4-15 #6).
 */
public class IdentifierAllocationService {

    private final IdentifierPolicyRepository policyRepository;
    private final IdentifierSequenceStore sequenceStore;
    private final Clock clock;

    public IdentifierAllocationService(
            IdentifierPolicyRepository policyRepository,
            IdentifierSequenceStore sequenceStore,
            Clock clock
    ) {
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository cannot be null");
        this.sequenceStore = Objects.requireNonNull(sequenceStore, "sequenceStore cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public IdentifierAllocationService(
            IdentifierPolicyRepository policyRepository,
            IdentifierSequenceStore sequenceStore
    ) {
        this(policyRepository, sequenceStore, Clock.systemUTC());
    }

    /**
     * Resolves the policy for the namespace/scope and atomically allocates a new business identifier.
     */
    public Uni<IdentifierAllocation> allocate(String namespace, IdentifierScope scope) {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        String periodKey = String.valueOf(today.getYear());

        return policyRepository.findActivePolicy(namespace, scope)
                .flatMap(maybePolicy -> {
                    if (maybePolicy.isEmpty()) {
                        return Uni.createFrom().failure(new IllegalStateException(
                                "No active identifier policy found for namespace: " + namespace));
                    }
                    IdentifierPolicy policy = maybePolicy.get();
                    if (!policy.isEffective(today)) {
                        return Uni.createFrom().failure(new IllegalStateException(
                                "Identifier policy for namespace " + namespace + " is not effective on " + today));
                    }

                    IdentifierSequenceStore.SequenceKey seqKey =
                            new IdentifierSequenceStore.SequenceKey(namespace, scope.toScopeKey(), periodKey);

                    return sequenceStore.allocateNext(seqKey)
                            .map(seq -> {
                                String formattedValue = policy.format().format(today.getYear(), seq);
                                BusinessIdentifier identifier = BusinessIdentifier.of(namespace, formattedValue);
                                return new IdentifierAllocation(identifier, policy.id(), scope, seq, now);
                            });
                });
    }
}
