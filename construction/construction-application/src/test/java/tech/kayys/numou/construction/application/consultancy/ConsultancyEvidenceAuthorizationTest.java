package tech.kayys.numou.construction.application.consultancy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultancyEvidenceAuthorizationTest {
    @Test
    void normalizesTenantAndRequiresVersionPinnedDocumentAccess() {
        UUID actor = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID deliverable = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        UUID version = UUID.randomUUID();

        var deliverableRequest = new ConsultancyEvidenceAuthorization.DeliverableAccess(
                " tenant-a ", actor, organization, deliverable,
                ConsultancyEvidenceAuthorization.Action.ATTACH_EVIDENCE);
        var documentRequest = new ConsultancyEvidenceAuthorization.DocumentAccess(
                "tenant-a", actor, organization, document, version,
                ConsultancyEvidenceAuthorization.Action.ATTACH_EVIDENCE);

        assertThat(deliverableRequest.tenantId()).isEqualTo("tenant-a");
        assertThat(documentRequest.documentVersionId()).isEqualTo(version);
        assertThat(documentRequest.tenantId()).isEqualTo(deliverableRequest.tenantId());
    }

    @Test
    void rejectsMissingTenantAndMissingDocumentVersion() {
        UUID actor = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new ConsultancyEvidenceAuthorization.DeliverableAccess(
                " ", actor, organization, id, ConsultancyEvidenceAuthorization.Action.VIEW_EVIDENCE))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ConsultancyEvidenceAuthorization.DocumentAccess(
                "tenant-a", actor, organization, id, null,
                ConsultancyEvidenceAuthorization.Action.VIEW_EVIDENCE))
                .isInstanceOf(NullPointerException.class);
    }
}
