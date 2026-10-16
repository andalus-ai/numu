package tech.kayys.syirkah.project.application.commercial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.application.commercial.command.*;
import tech.kayys.syirkah.project.application.commercial.handler.*;
import tech.kayys.syirkah.project.application.support.*;
import tech.kayys.syirkah.project.domain.commercial.ChangeImpact;
import tech.kayys.syirkah.project.domain.commercial.ClaimType;
import tech.kayys.syirkah.project.domain.commercial.ContractType;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Commercial command handlers")
class CommercialCommandHandlerTest {

    private final ProjectId projectId = ProjectId.generate();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private final InMemoryProjectContractRepository contracts = new InMemoryProjectContractRepository();
    private final InMemoryChangeOrderRepository changeOrders = new InMemoryChangeOrderRepository();
    private final InMemoryProjectClaimRepository claims = new InMemoryProjectClaimRepository();
    private final InMemoryProjectRetentionRepository retentions = new InMemoryProjectRetentionRepository();
    private final InMemoryProjectAdvanceRepository advances = new InMemoryProjectAdvanceRepository();

    private final CreateProjectContractHandler createContract = new CreateProjectContractHandler(contracts, events);
    private final ApproveProjectContractHandler approveContract = new ApproveProjectContractHandler(contracts, events);
    private final ActivateProjectContractHandler activateContract = new ActivateProjectContractHandler(contracts, events);
    private final CompleteProjectContractHandler completeContract = new CompleteProjectContractHandler(contracts, events);

    private final CreateChangeOrderHandler createCO = new CreateChangeOrderHandler(changeOrders, events);
    private final ApproveChangeOrderHandler approveCO = new ApproveChangeOrderHandler(changeOrders, events);
    private final ApplyChangeOrderHandler applyCO = new ApplyChangeOrderHandler(changeOrders, events);

    private final CreateClaimHandler createClaim = new CreateClaimHandler(claims, events);
    private final CreateRetentionHandler createRetention = new CreateRetentionHandler(retentions, events);
    private final ReleaseRetentionHandler releaseRetention = new ReleaseRetentionHandler(retentions, events);

    private final CreateAdvanceHandler createAdvance = new CreateAdvanceHandler(advances, events);
    private final MarkAdvanceReceivedHandler markReceived = new MarkAdvanceReceivedHandler(advances, events);
    private final ApplyAdvanceHandler applyAdvance = new ApplyAdvanceHandler(advances, events);

    @Test
    void contractLifecycle() {
        var cmd = new CreateProjectContractCommand(
                projectId,
                ContractType.CUSTOMER,
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                Money.of(new BigDecimal("1000000.00"), "IDR")
        );
        var id = createContract.handle(cmd).await().indefinitely().orElseThrow();
        assertTrue(createContract.handle(cmd).await().indefinitely().isFailure());

        assertTrue(approveContract.handle(new ApproveProjectContractCommand(id)).await().indefinitely().isSuccess());
        assertTrue(activateContract.handle(new ActivateProjectContractCommand(id)).await().indefinitely().isSuccess());
        assertTrue(completeContract.handle(new CompleteProjectContractCommand(id)).await().indefinitely().isSuccess());
    }

    @Test
    void changeOrderLifecycle() {
        var contractCmd = new CreateProjectContractCommand(
                projectId,
                ContractType.CUSTOMER,
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                Money.of(new BigDecimal("500000.00"), "IDR")
        );
        var contractId = createContract.handle(contractCmd).await().indefinitely().orElseThrow();

        var coCmd = new CreateChangeOrderCommand(
                projectId,
                contractId,
                null,
                "CO-101",
                "Extra Server",
                "Add nodes",
                new ChangeImpact(Money.of(new BigDecimal("50000.00"), "IDR"), Period.ofDays(7), true)
        );
        var coId = createCO.handle(coCmd).await().indefinitely().orElseThrow();
        assertTrue(approveCO.handle(new ApproveChangeOrderCommand(coId)).await().indefinitely().isSuccess());
        assertTrue(applyCO.handle(new ApplyChangeOrderCommand(coId)).await().indefinitely().isSuccess());
    }

    @Test
    void claimRetentionAdvanceLifecycle() {
        var contractCmd = new CreateProjectContractCommand(
                projectId,
                ContractType.CUSTOMER,
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                Money.of(new BigDecimal("500000.00"), "IDR")
        );
        var contractId = createContract.handle(contractCmd).await().indefinitely().orElseThrow();

        var claimId = createClaim.handle(new CreateClaimCommand(
                projectId, contractId, ClaimType.PROJECT, "CLM-001", "Overtime", "Overtime", Money.of(new BigDecimal("20000.00"), "IDR")
        )).await().indefinitely().orElseThrow();
        assertNotNull(claimId);

        var retentionId = createRetention.handle(new CreateRetentionCommand(
                projectId, contractId, Money.of(new BigDecimal("25000.00"), "IDR")
        )).await().indefinitely().orElseThrow();
        assertTrue(releaseRetention.handle(
                new ReleaseRetentionCommand(retentionId, Money.of(new BigDecimal("10000.00"), "IDR"))
        ).await().indefinitely().isSuccess());

        var advId = createAdvance.handle(new CreateAdvanceCommand(
                projectId, contractId, Money.of(new BigDecimal("100000.00"), "IDR")
        )).await().indefinitely().orElseThrow();
        assertTrue(markReceived.handle(new MarkAdvanceReceivedCommand(advId)).await().indefinitely().isSuccess());
        assertTrue(applyAdvance.handle(
                new ApplyAdvanceCommand(advId, Money.of(new BigDecimal("40000.00"), "IDR"))
        ).await().indefinitely().isSuccess());
    }
}
