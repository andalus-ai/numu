package tech.kayys.syirkah.project.domain.commercial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.event.ProjectContractActivated;
import tech.kayys.syirkah.project.domain.commercial.event.ProjectContractApproved;
import tech.kayys.syirkah.project.domain.commercial.event.ProjectContractCompleted;
import tech.kayys.syirkah.project.domain.commercial.event.ProjectContractCreated;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectContract domain invariants and lifecycle")
class ProjectContractTest {

    private static final ProjectId PROJECT_ID = ProjectId.generate();
    private static final DateRange PERIOD = new DateRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
    );
    private static final Money INITIAL_VALUE = Money.of(new BigDecimal("100000.00"), "IDR");

    private ProjectContract createContract() {
        return ProjectContract.create(
                ProjectContractId.generate(),
                PROJECT_ID,
                ContractType.CUSTOMER,
                PERIOD,
                INITIAL_VALUE
        );
    }

    @Test
    void createsContractInDraftAndRaisesEvent() {
        var contract = createContract();

        assertEquals(ContractStatus.DRAFT, contract.status());
        assertEquals(INITIAL_VALUE, contract.contractValue().original());
        assertEquals(INITIAL_VALUE, contract.contractValue().current());

        var events = contract.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(ProjectContractCreated.class, events.getFirst());
    }

    @Test
    void rejectsNegativeInitialValue() {
        assertThrows(IllegalArgumentException.class, () ->
                ProjectContract.create(
                        ProjectContractId.generate(),
                        PROJECT_ID,
                        ContractType.CUSTOMER,
                        PERIOD,
                        Money.of(new BigDecimal("-1.00"), "IDR")
                )
        );
    }

    @Test
    void addsPartiesAndGuardsUniqueness() {
        var contract = createContract();
        UUID partyId = UUID.randomUUID();

        contract.addParty(new ContractParty(partyId, ContractPartyRole.CLIENT, "PT Client"));
        assertEquals(1, contract.parties().size());

        assertThrows(IllegalStateException.class, () ->
                contract.addParty(new ContractParty(partyId, ContractPartyRole.CLIENT, "Duplicate"))
        );
    }

    @Test
    void transitionsThroughFullLifecycle() {
        var contract = createContract();
        contract.pullDomainEvents();

        contract.submitForReview();
        assertEquals(ContractStatus.UNDER_REVIEW, contract.status());

        contract.approve();
        assertEquals(ContractStatus.APPROVED, contract.status());
        var approvedEvent = contract.pullDomainEvents();
        assertInstanceOf(ProjectContractApproved.class, approvedEvent.getFirst());

        contract.activate();
        assertEquals(ContractStatus.ACTIVE, contract.status());
        var activatedEvent = contract.pullDomainEvents();
        assertInstanceOf(ProjectContractActivated.class, activatedEvent.getFirst());

        contract.suspend();
        assertEquals(ContractStatus.SUSPENDED, contract.status());

        contract.resume();
        assertEquals(ContractStatus.ACTIVE, contract.status());

        contract.complete();
        assertEquals(ContractStatus.COMPLETED, contract.status());
        var completedEvent = contract.pullDomainEvents();
        assertInstanceOf(ProjectContractCompleted.class, completedEvent.getFirst());
    }

    @Test
    void cannotActivateWithoutApproval() {
        var contract = createContract();
        assertThrows(InvalidStateException.class, contract::activate);
    }

    @Test
    void changesValuePreservingOriginal() {
        var contract = createContract();
        Money updated = Money.of(new BigDecimal("120000.00"), "IDR");

        contract.changeValue(updated);
        assertEquals(INITIAL_VALUE, contract.contractValue().original());
        assertEquals(updated, contract.contractValue().current());

        assertThrows(IllegalArgumentException.class, () ->
                contract.changeValue(Money.of(new BigDecimal("100.00"), "USD"))
        );
    }
}
