package tech.kayys.syirkah.construction.domain.boq;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BoqTest {
    @Test
    void shouldCreateBoqAndCalculateItemAmount() {
        var projectId = UUID.randomUUID();
        var boq = Boq.create(projectId, "Main Tower BOQ");

        assertThat(boq.status()).isEqualTo(BoqStatus.DRAFT);
        assertThat(boq.currentRevision()).isEqualTo(1);

        boq.submitForReview();
        assertThat(boq.status()).isEqualTo(BoqStatus.UNDER_REVIEW);

        boq.approve();
        assertThat(boq.status()).isEqualTo(BoqStatus.APPROVED);

        var qty = new BoqQuantity(BigDecimal.valueOf(100), "m3");
        var rate = new BoqRate(BigDecimal.valueOf(1500000), Currency.getInstance("IDR"), "m3");
        var item = BoqItem.create(
                boq.id().value(),
                UUID.randomUUID(),
                "03.01.01",
                "Ready-mix Concrete C30",
                BoqItemType.MATERIAL,
                qty,
                rate
        );

        assertThat(item.amount()).isEqualByComparingTo(BigDecimal.valueOf(150000000));
    }
}
