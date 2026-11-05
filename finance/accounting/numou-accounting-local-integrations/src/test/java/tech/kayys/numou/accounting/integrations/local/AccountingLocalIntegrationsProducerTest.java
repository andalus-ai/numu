package tech.kayys.numou.accounting.integrations.local;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountingLocalIntegrationsProducerTest {

    private final AccountingLocalIntegrationsProducer producer =
            new AccountingLocalIntegrationsProducer();

    @Test
    void createsOptionalInMemoryInventoryIntegration() {
        assertThat(producer.inventoryService()).isNotNull();
    }

    @Test
    void createsOptionalInMemoryBudgetIntegrations() {
        assertThat(producer.budgetService()).isNotNull();
        assertThat(producer.budgetingService()).isNotNull();
    }

    @Test
    void createsOptionalInMemoryProjectFinanceIntegration() {
        assertThat(producer.projectFinanceService()).isNotNull();
    }
}
