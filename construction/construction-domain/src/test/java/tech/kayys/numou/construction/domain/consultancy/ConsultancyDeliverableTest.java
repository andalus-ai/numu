package tech.kayys.numou.construction.domain.consultancy;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ConsultancyDeliverableTest {
    @Test
    void onlyClientCanReviewAndRejectedDeliverableMustBeRevisedBeforeResubmission() {
        var engagementId = ConsultancyEngagementId.generate();
        var client = UUID.randomUUID();
        var consultant = UUID.randomUUID();
        var otherOrganization = UUID.randomUUID();
        var deliverable = ConsultancyDeliverable.create(
                engagementId, client, consultant, "D-01", "Concept design", "Concept design report");

        assertThatThrownBy(() -> deliverable.accept(otherOrganization, "Looks good"))
                .isInstanceOf(IllegalArgumentException.class);
        deliverable.submit();
        assertThatThrownBy(deliverable::submit).isInstanceOf(IllegalStateException.class);
        deliverable.reject(client, "Please address the site access assumptions");

        assertThat(deliverable.status()).isEqualTo(ConsultancyDeliverableStatus.REJECTED);
        assertThatThrownBy(() -> deliverable.accept(client, "Accepted"))
                .isInstanceOf(IllegalStateException.class);

        deliverable.revise("Concept design — revision 2", "Updated site access assumptions");
        assertThat(deliverable.revision()).isEqualTo(2);
        assertThat(deliverable.status()).isEqualTo(ConsultancyDeliverableStatus.DRAFT);
        assertThat(deliverable.reviewedByOrganizationId()).isNull();

        deliverable.submit();
        deliverable.accept(client, "Accepted for the next design gate");
        assertThat(deliverable.status()).isEqualTo(ConsultancyDeliverableStatus.ACCEPTED);
        assertThat(deliverable.reviewedByOrganizationId()).isEqualTo(client);
    }

    @Test
    void clientAndConsultantMustBeDifferentOrganizations() {
        var organization = UUID.randomUUID();

        assertThatThrownBy(() -> ConsultancyDeliverable.create(
                ConsultancyEngagementId.generate(), organization, organization,
                "D-01", "Report", "Technical report"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
