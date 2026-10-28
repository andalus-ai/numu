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
    void reconstitutionRestoresReviewStateAndRejectsInconsistentReviewMetadata() {
        var engagementId = ConsultancyEngagementId.generate();
        var client = UUID.randomUUID();
        var consultant = UUID.randomUUID();
        var id = ConsultancyDeliverableId.generate();
        var restored = ConsultancyDeliverable.reconstitute(id, engagementId, client, consultant,
                "D-02", "Structural report", "Signed structural report", 3,
                ConsultancyDeliverableStatus.REJECTED, client, "Missing calculation appendix");

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.engagementId()).isEqualTo(engagementId);
        assertThat(restored.revision()).isEqualTo(3);
        assertThat(restored.status()).isEqualTo(ConsultancyDeliverableStatus.REJECTED);
        assertThat(restored.reviewedByOrganizationId()).isEqualTo(client);
        assertThat(restored.reviewComment()).isEqualTo("Missing calculation appendix");
        assertThatThrownBy(() -> ConsultancyDeliverable.reconstitute(id, engagementId, client, consultant,
                "D-02", "Structural report", "Signed structural report", 3,
                ConsultancyDeliverableStatus.REJECTED, consultant, "Wrong reviewer"))
                .isInstanceOf(IllegalArgumentException.class);
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
