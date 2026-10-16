package tech.kayys.syirkah.construction.domain.variation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ChangeOrderTest {
    @Test
    void shouldCreateAndApproveChangeOrder() {
        var projectId = UUID.randomUUID();
        var co = ChangeOrder.create(projectId, "CO-001", "Foundation Depth Increase", ChangeOrderType.FIELD_CONDITION, "Encountered bedrock");

        assertThat(co.status()).isEqualTo(ChangeOrderStatus.DRAFT);
        co.submit();
        assertThat(co.status()).isEqualTo(ChangeOrderStatus.SUBMITTED);
        co.approve();
        assertThat(co.status()).isEqualTo(ChangeOrderStatus.APPROVED);

        var item = ChangeOrderItem.create(
                co.id().value(),
                UUID.randomUUID(),
                VariationType.ADD_QUANTITY,
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(2000000),
                "Extra rock excavation"
        );

        assertThat(item.costImpact()).isEqualByComparingTo(BigDecimal.valueOf(100000000));
    }
}
