package tech.kayys.numou.construction.adapter.postgres.consultancy;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.accounting.domain.document.DocumentId;
import tech.kayys.numou.accounting.domain.document.DocumentLink;
import tech.kayys.numou.accounting.domain.document.DocumentVersionId;
import tech.kayys.numou.construction.spi.consultancy.ConsultancyEvidenceLink;
import tech.kayys.numou.construction.spi.consultancy.ConsultancyEvidenceLinkStore;
import tech.kayys.numou.document.application.port.DocumentLinkRepository;

import java.util.List;
import java.util.UUID;

/**
 * Construction SPI bridge to Document's application-owned link repository.
 * No Document tables, entities, or file storage are accessed from Construction.
 */
@ApplicationScoped
public class DocumentConsultancyEvidenceLinkStore implements ConsultancyEvidenceLinkStore {
    private static final String AGGREGATE_TYPE = "ConsultancyDeliverable";

    @Inject
    DocumentLinkRepository documentLinks;

    @Override
    public Uni<ConsultancyEvidenceLink> attach(
            String tenantId, UUID deliverableId, UUID documentId, UUID documentVersionId,
            String relationship, UUID actorId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("tenantId is required"));
        }
        if (deliverableId == null || documentId == null || documentVersionId == null || actorId == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Evidence identifiers and actor are required"));
        }
        DocumentLink requested = DocumentLink.create(
                DocumentId.of(documentId.toString()),
                DocumentVersionId.of(documentVersionId.toString()),
                AGGREGATE_TYPE, deliverableId.toString(), relationship, actorId);
        return documentLinks.save(tenantId.trim(), requested)
                .chain(() -> documentLinks.findByAggregate(
                        tenantId.trim(), AGGREGATE_TYPE, deliverableId.toString()))
                .map(rows -> rows.stream()
                        .filter(link -> link.documentId().value().equals(documentId.toString())
                                && link.documentVersionId() != null
                                && link.documentVersionId().value().equals(documentVersionId.toString())
                                && link.relationship().equals(relationship))
                        .findFirst()
                        .map(link -> {
                            if (link.linkedBy() == null) {
                                throw new tech.kayys.numou.construction.application.consultancy
                                        .ConsultancyEvidencePermanentFailureException(
                                        "An existing evidence relationship has no recorded actor attribution");
                            }
                            return new ConsultancyEvidenceLink(
                                    UUID.fromString(link.id().value()), deliverableId,
                                    UUID.fromString(link.documentId().value()),
                                    UUID.fromString(link.documentVersionId().value()),
                                    link.relationship(), link.linkedBy(), link.linkedAt());
                        })
                        .orElseThrow(() -> new IllegalStateException(
                                "Document repository did not return the persisted evidence relationship")));
    }

    @Override
    public Uni<List<ConsultancyEvidenceLink>> findByDeliverable(String tenantId, UUID deliverableId) {
        if (tenantId == null || tenantId.isBlank() || deliverableId == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Tenant and deliverable are required"));
        }
        return documentLinks.findByAggregate(tenantId.trim(), AGGREGATE_TYPE, deliverableId.toString())
                .map(rows -> rows.stream()
                        .filter(link -> link.documentVersionId() != null && link.linkedBy() != null)
                        .map(link -> new ConsultancyEvidenceLink(
                                UUID.fromString(link.id().value()), deliverableId,
                                UUID.fromString(link.documentId().value()),
                                UUID.fromString(link.documentVersionId().value()),
                                link.relationship(), link.linkedBy(), link.linkedAt()))
                        .toList());
    }
}
