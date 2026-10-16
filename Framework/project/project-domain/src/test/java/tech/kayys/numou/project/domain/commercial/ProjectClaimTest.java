package tech.kayys.syirkah.project.domain.commercial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.event.ClaimAccepted;
import tech.kayys.syirkah.project.domain.commercial.event.ClaimRejected;
import tech.kayys.syirkah.project.domain.commercial.event.ClaimSettled;
import tech.kayys.syirkah.project.domain.commercial.event.ClaimSubmitted;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectClaim domain invariants and lifecycle")
class ProjectClaimTest {

    private static final ProjectId PROJECT_ID = ProjectId.generate();
    private static final ProjectContractId CONTRACT_ID = ProjectContractId.generate();
    private static final Money CLAIMED = Money.of(new BigDecimal("15000.00"), "IDR");

    private ProjectClaim createClaim() {
        return ProjectClaim.create(
                ProjectClaimId.generate(),
                PROJECT_ID,
                CONTRACT_ID,
                ClaimType.PROJECT,
                "CLM-001",
                "Weather delay compensation",
                "Flooding caused 14 day delay",
                CLAIMED
        );
    }

    @Test
    void createsClaimInDraft() {
        var claim = createClaim();
        assertEquals(ClaimStatus.DRAFT, claim.status());
        assertEquals(CLAIMED, claim.claimedAmount());
    }

    @Test
    void submitsClaimAndAcceptsFullAmount() {
        var claim = createClaim();

        claim.submit();
        assertEquals(ClaimStatus.SUBMITTED, claim.status());
        assertInstanceOf(ClaimSubmitted.class, claim.pullDomainEvents().getFirst());

        claim.startReview();
        assertEquals(ClaimStatus.UNDER_REVIEW, claim.status());

        claim.accept(CLAIMED);
        assertEquals(ClaimStatus.ACCEPTED, claim.status());
        assertEquals(CLAIMED, claim.acceptedAmount());
        assertInstanceOf(ClaimAccepted.class, claim.pullDomainEvents().getFirst());

        claim.settle();
        assertEquals(ClaimStatus.SETTLED, claim.status());
        assertInstanceOf(ClaimSettled.class, claim.pullDomainEvents().getFirst());
    }

    @Test
    void rejectsClaimUnderReview() {
        var claim = createClaim();
        claim.submit();
        claim.startReview();
        claim.pullDomainEvents();

        claim.reject();
        assertEquals(ClaimStatus.REJECTED, claim.status());
        assertInstanceOf(ClaimRejected.class, claim.pullDomainEvents().getFirst());
    }

    @Test
    void partiallyAcceptsClaim() {
        var claim = createClaim();
        claim.submit();
        claim.startReview();

        Money partial = Money.of(new BigDecimal("10000.00"), "IDR");
        claim.partiallyAccept(partial);
        assertEquals(ClaimStatus.PARTIALLY_ACCEPTED, claim.status());
        assertEquals(partial, claim.acceptedAmount());
    }

    @Test
    void cannotAcceptWithoutReview() {
        var claim = createClaim();
        assertThrows(InvalidStateException.class, () -> claim.accept(CLAIMED));
    }
}
