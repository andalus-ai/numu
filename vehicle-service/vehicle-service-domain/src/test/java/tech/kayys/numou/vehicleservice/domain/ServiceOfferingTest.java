package tech.kayys.numou.vehicleservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ServiceOfferingTest {
    @Test
    void requiresPositiveDurationAndNonBlankIdentity() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                ServiceOffering.create(ServiceOfferingId.generate(), "tenant-a", "OIL", "Oil service", "",
                        ServiceCategory.ROUTINE_MAINTENANCE, 0));
        assertThatIllegalArgumentException().isThrownBy(() ->
                ServiceOffering.create(ServiceOfferingId.generate(), "tenant-a", " ", "Oil service", "",
                        ServiceCategory.ROUTINE_MAINTENANCE, 30));
    }

    @Test
    void canBeActivatedRevisedAndRetiredButNotRevisedAfterRetirement() {
        ServiceOffering offering = ServiceOffering.create(ServiceOfferingId.generate(), "tenant-a", "OIL",
                "Oil service", "", ServiceCategory.ROUTINE_MAINTENANCE, 30);

        offering.activate();
        offering.revise("Full oil service", "Oil and filter", ServiceCategory.ROUTINE_MAINTENANCE, 45);
        offering.retire();

        assertThat(offering.status()).isEqualTo(ServiceOfferingStatus.RETIRED);
        assertThat(offering.estimatedDurationMinutes()).isEqualTo(45);
        assertThatIllegalStateException().isThrownBy(() -> offering.revise("Again", "", ServiceCategory.OTHER, 20));
        assertThatIllegalStateException().isThrownBy(offering::activate);
    }
}
