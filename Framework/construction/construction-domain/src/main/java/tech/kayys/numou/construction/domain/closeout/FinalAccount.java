package tech.kayys.syirkah.construction.domain.closeout;

import tech.kayys.syirkah.construction.domain.closeout.event.FinalAccountSettled;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class FinalAccount extends AbstractAggregateRoot<FinalAccountId> {
    private final UUID contractId;
    private final BigDecimal finalContractAmount;
    private FinalAccountStatus status;

    private FinalAccount(FinalAccountId id, UUID contractId, BigDecimal finalContractAmount) {
        super(id);
        this.contractId = Objects.requireNonNull(contractId);
        this.finalContractAmount = Objects.requireNonNull(finalContractAmount);
        this.status = FinalAccountStatus.DRAFT;
    }

    public static FinalAccount prepare(UUID contractId, BigDecimal finalContractAmount) {
        return new FinalAccount(FinalAccountId.generate(), contractId, finalContractAmount);
    }

    public void settle() {
        this.status = FinalAccountStatus.SETTLED;
        raise(new FinalAccountSettled(UUID.randomUUID(), Instant.now(), id().value(), contractId, finalContractAmount));
    }

    public UUID contractId() { return contractId; }
    public BigDecimal finalContractAmount() { return finalContractAmount; }
    public FinalAccountStatus status() { return status; }
}
