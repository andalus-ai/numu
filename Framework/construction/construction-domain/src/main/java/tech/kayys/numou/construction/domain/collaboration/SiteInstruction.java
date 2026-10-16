package tech.kayys.syirkah.construction.domain.collaboration;

import tech.kayys.syirkah.construction.domain.collaboration.event.SiteInstructionIssued;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SiteInstruction extends AbstractAggregateRoot<SiteInstructionId> {
    private final UUID siteId;
    private final String instructionNumber;
    private final String details;
    private SiteInstructionStatus status;

    private SiteInstruction(SiteInstructionId id, UUID siteId, String instructionNumber, String details) {
        super(id);
        this.siteId = Objects.requireNonNull(siteId);
        this.instructionNumber = Objects.requireNonNull(instructionNumber);
        this.details = Objects.requireNonNull(details);
        this.status = SiteInstructionStatus.ISSUED;
    }

    public static SiteInstruction issue(UUID siteId, String instructionNumber, String details) {
        var inst = new SiteInstruction(SiteInstructionId.generate(), siteId, instructionNumber, details);
        inst.raise(new SiteInstructionIssued(UUID.randomUUID(), Instant.now(), inst.id().value(), siteId, instructionNumber));
        return inst;
    }

    public void acknowledge() { this.status = SiteInstructionStatus.ACKNOWLEDGED; }
    public void comply() { this.status = SiteInstructionStatus.COMPLIED; }

    public UUID siteId() { return siteId; }
    public String instructionNumber() { return instructionNumber; }
    public String details() { return details; }
    public SiteInstructionStatus status() { return status; }
}
