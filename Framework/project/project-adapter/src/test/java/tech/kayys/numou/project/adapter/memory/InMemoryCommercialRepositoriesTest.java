package tech.kayys.numou.project.adapter.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.DateRange;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.project.domain.commercial.*;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("In-memory commercial repositories")
class InMemoryCommercialRepositoriesTest {

    private final ProjectId projectId = ProjectId.generate();
    private final InMemoryProjectContractRepository contracts = new InMemoryProjectContractRepository();
    private final InMemoryChangeOrderRepository changeOrders = new InMemoryChangeOrderRepository();
    private final InMemoryProjectClaimRepository claims = new InMemoryProjectClaimRepository();
    private final InMemoryProjectRetentionRepository retentions = new InMemoryProjectRetentionRepository();
    private final InMemoryProjectAdvanceRepository advances = new InMemoryProjectAdvanceRepository();

    @Test
    void savesAndFindsContract() {
        var contract = ProjectContract.create(
                ProjectContractId.generate(),
                projectId,
                ContractType.CUSTOMER,
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                Money.of(new BigDecimal("100000.00"), "IDR")
        );

        contracts.save(contract).toCompletableFuture().join();
        assertTrue(contracts.existsById(contract.id()).toCompletableFuture().join());

        var found = contracts.findByProjectId(projectId).toCompletableFuture().join().orElseThrow();
        assertEquals(contract.id(), found.id());

        contracts.deleteById(contract.id()).toCompletableFuture().join();
        assertFalse(contracts.existsById(contract.id()).toCompletableFuture().join());
    }

    @Test
    void savesAndFindsChangeOrder() {
        var contractId = ProjectContractId.generate();
        var co = ChangeOrder.create(
                ChangeOrderId.generate(),
                projectId,
                contractId,
                null,
                "CO-001",
                "Scope extension",
                "New feature",
                new ChangeImpact(Money.of(new BigDecimal("1000.00"), "IDR"), Period.ofDays(3), true)
        );

        changeOrders.save(co).toCompletableFuture().join();
        assertTrue(changeOrders.existsById(co.id()).toCompletableFuture().join());

        var foundByNum = changeOrders.findByNumber(projectId, "CO-001").toCompletableFuture().join().orElseThrow();
        assertEquals(co.id(), foundByNum.id());

        var list = changeOrders.findByContractId(contractId).toCompletableFuture().join();
        assertEquals(1, list.size());
    }

    @Test
    void savesAndFindsClaimsAdvancesAndRetentions() {
        var contractId = ProjectContractId.generate();

        var claim = ProjectClaim.create(
                ProjectClaimId.generate(),
                projectId,
                contractId,
                ClaimType.PROJECT,
                "CLM-1",
                "Title",
                "Desc",
                Money.of(new BigDecimal("5000.00"), "IDR")
        );
        claims.save(claim).toCompletableFuture().join();
        assertEquals(1, claims.findByProjectId(projectId).toCompletableFuture().join().size());

        var adv = ProjectAdvance.create(
                ProjectAdvanceId.generate(),
                projectId,
                contractId,
                Money.of(new BigDecimal("20000.00"), "IDR")
        );
        advances.save(adv).toCompletableFuture().join();
        assertEquals(1, advances.findByProjectId(projectId).toCompletableFuture().join().size());

        var ret = ProjectRetention.create(
                ProjectRetentionId.generate(),
                projectId,
                contractId,
                Money.of(new BigDecimal("10000.00"), "IDR")
        );
        retentions.save(ret).toCompletableFuture().join();
        assertEquals(1, retentions.findOpenByProjectId(projectId).toCompletableFuture().join().size());
    }
}
