package tech.kayys.numou.accounting.domain.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentLinkTest {
    @Test
    void supportsLegacyLinksWithoutPinnedVersion() {
        var link = new DocumentLink(
                DocumentLinkId.generate(), DocumentId.of("10000000-0000-0000-0000-000000000001"),
                "ConsultancyDeliverable", "deliverable-1", "SIGNED_REPORT", Instant.parse("2026-10-29T00:00:00Z"));

        assertNull(link.documentVersionId());
    }

    @Test
    void createsVersionPinnedEvidenceLink() {
        var documentId = DocumentId.of("10000000-0000-0000-0000-000000000001");
        var versionId = DocumentVersionId.of("20000000-0000-0000-0000-000000000002");

        var link = DocumentLink.create(documentId, versionId, "ConsultancyDeliverable", "deliverable-1", "SIGNED_REPORT");

        assertEquals(documentId, link.documentId());
        assertEquals(versionId, link.documentVersionId());
        assertEquals("ConsultancyDeliverable", link.aggregateType());
        assertEquals("deliverable-1", link.aggregateId());
        assertEquals("SIGNED_REPORT", link.relationship());
    }

    @Test
    void createsVersionPinnedEvidenceLinkWithActorAttribution() {
        var actor = UUID.randomUUID();
        var link = DocumentLink.create(
                DocumentId.of("10000000-0000-0000-0000-000000000001"),
                DocumentVersionId.of("20000000-0000-0000-0000-000000000002"),
                "ConsultancyDeliverable", "deliverable-1", "SIGNED_REPORT", actor);

        assertEquals(actor, link.linkedBy());
        assertNotNull(link.linkedAt());
    }

    @Test
    void rejectsBlankRelationshipReferences() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentLink(
                DocumentLinkId.generate(), DocumentId.of("10000000-0000-0000-0000-000000000001"),
                null, " ", "deliverable-1", "SIGNED_REPORT", Instant.now()));
    }
}
