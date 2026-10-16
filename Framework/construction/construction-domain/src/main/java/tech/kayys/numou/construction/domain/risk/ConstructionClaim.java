package tech.kayys.syirkah.construction.domain.risk;

import tech.kayys.syirkah.construction.domain.risk.event.ConstructionClaimSubmitted;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ConstructionClaim extends AbstractAggregateRoot<ConstructionClaimId> {
    private final UUID contractId;
    private final String claimReference;
    private final String narrative;
    private ConstructionClaimStatus status;

    private ConstructionClaim(ConstructionClaimId id, UUID contractId, String claimReference, String narrative) {
        super(id);
        this.contractId = Objects.requireNonNull(contractId);
        this.claimReference = Objects.requireNonNull(claimReference);
        this.narrative = Objects.requireNonNull(narrative);
        this.status = ConstructionClaimStatus.NOTIFIED;
    }

    public static ConstructionClaim submit(UUID contractId, String claimReference, String narrative) {
        var claim = new ConstructionClaim(ConstructionClaimId.generate(), contractId, claimReference, narrative);
        claim.status = ConstructionClaimStatus.SUBMITTED;
        claim.raise(new ConstructionClaimSubmitted(UUID.randomUUID(), Instant.now(), claim.id().value(), contractId, claimReference));
        return claim;
    }

    public UUID contractId() { return contractId; }
    public String claimReference() { return claimReference; }
    public String narrative() { return narrative; }
    public ConstructionClaimStatus status() { return status; }
}
