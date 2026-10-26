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

    @Test
    void rejectedRevisionDoesNotPartiallyMutateTheOffering() {
        ServiceOffering offering = ServiceOffering.create(ServiceOfferingId.generate(), "tenant-a", "OIL",
                "Oil service", "original", ServiceCategory.ROUTINE_MAINTENANCE, 30);

        assertThatIllegalArgumentException().isThrownBy(() ->
                offering.revise("Changed name", "changed", ServiceCategory.OTHER, -1));

        assertThat(offering.name()).isEqualTo("Oil service");
        assertThat(offering.description()).isEqualTo("original");
        assertThat(offering.category()).isEqualTo(ServiceCategory.ROUTINE_MAINTENANCE);
        assertThat(offering.estimatedDurationMinutes()).isEqualTo(30);
    }
}
