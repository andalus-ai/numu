package tech.kayys.syirkah.construction.domain.contract;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ConstructionContractTest {
    @Test
    void shouldCreateAndActivateContract() {
        var projectId = UUID.randomUUID();
        var client = new ContractParty(UUID.randomUUID(), "Project Owner Corp", ContractPartyRole.CLIENT);
        var contractor = new ContractParty(UUID.randomUUID(), "Main Contractor PT", ContractPartyRole.MAIN_CONTRACTOR);
        var value = new ContractValue(BigDecimal.valueOf(50000000000L), Currency.getInstance("IDR"));

        var contract = ConstructionContract.create(projectId, "CTR-2026-001", "Bandung Office EPC", client, contractor, value);
        assertThat(contract.status()).isEqualTo(ContractStatus.DRAFT);

        contract.activate();
        assertThat(contract.status()).isEqualTo(ContractStatus.ACTIVE);

        var events = contract.pullDomainEvents();
        assertThat(events).hasSize(2);
    }
}
