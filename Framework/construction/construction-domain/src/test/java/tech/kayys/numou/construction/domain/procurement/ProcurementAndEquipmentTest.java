package tech.kayys.syirkah.construction.domain.procurement;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ProcurementAndEquipmentTest {
    @Test
    void shouldCreateMaterialRequirementAndAddItem() {
        var projectId = UUID.randomUUID();
        var siteId = UUID.randomUUID();
        var req = MaterialRequirement.create(projectId, siteId);

        assertThat(req.status()).isEqualTo(MaterialRequirementStatus.DRAFT);

        var item = new RequirementItem(UUID.randomUUID(), "REBAR-D16", "Deformed Rebar 16mm", BigDecimal.valueOf(5000), "kg", LocalDate.now().plusDays(10));
        req.addItem(item);
        assertThat(req.items()).hasSize(1);

        req.submit();
        assertThat(req.status()).isEqualTo(MaterialRequirementStatus.SUBMITTED);
        req.approve();
        assertThat(req.status()).isEqualTo(MaterialRequirementStatus.APPROVED);
    }
}
