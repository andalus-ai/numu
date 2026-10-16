package tech.kayys.syirkah.construction.domain.progress;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ProgressMeasurementTest {
    @Test
    void shouldRecordAndApproveProgress() {
        var projectId = UUID.randomUUID();
        var boqId = UUID.randomUUID();
        var boqItemId = UUID.randomUUID();
        var qty = new MeasurementQuantity(BigDecimal.valueOf(25), "m3");

        var progress = ProgressMeasurement.record(
                projectId,
                boqId,
                boqItemId,
                LocalDate.now(),
                MeasurementType.DAILY,
                qty,
                "Pouring slab completed"
        );

        assertThat(progress.status()).isEqualTo(ProgressStatus.DRAFT);
        progress.submit();
        assertThat(progress.status()).isEqualTo(ProgressStatus.SUBMITTED);
        progress.approve();
        assertThat(progress.status()).isEqualTo(ProgressStatus.APPROVED);

        var events = progress.pullDomainEvents();
        assertThat(events).hasSize(2);
    }
}
