package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.construction.application.consultancy.ConsultancyEvidenceOperationStore.ClaimedConsultancyEvidenceOperation;
import tech.kayys.numou.construction.spi.consultancy.ConsultancyEvidenceLink;
import tech.kayys.numou.construction.spi.consultancy.ConsultancyEvidenceLinkStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultancyEvidenceOperationProcessorTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test
    void authorizesBothSidesAndMarksLinkedOnlyAfterDocumentReturnsMatchingLink() {
        var operation = operation();
        var claim = claim(operation);
        var store = new FakeOperationStore();
        var authorization = new FakeAuthorization();
        var links = new FakeLinks(operation);
        var processor = new ConsultancyEvidenceOperationProcessor(store, authorization, links);

        Boolean completed = processor.process(claim, 5).await().indefinitely();

        assertThat(completed).isTrue();
        assertThat(authorization.deliverableChecked).isTrue();
        assertThat(authorization.documentChecked).isTrue();
        assertThat(links.attached).isTrue();
        assertThat(store.linked).isTrue();
        assertThat(store.retryable).isFalse();
    }

    @Test
    void deniedAuthorizationBecomesTerminalAndNeverCallsDocument() {
        var operation = operation();
        var claim = claim(operation);
        var store = new FakeOperationStore();
        var authorization = new FakeAuthorization();
        authorization.denyDeliverable = true;
        var links = new FakeLinks(operation);
        var processor = new ConsultancyEvidenceOperationProcessor(store, authorization, links);

        Boolean terminal = processor.process(claim, 5).await().indefinitely();

        assertThat(terminal).isTrue();
        assertThat(store.terminal).isTrue();
        assertThat(links.attached).isFalse();
        assertThat(store.linked).isFalse();
    }

    private static ConsultancyEvidenceOperation operation() {
        return ConsultancyEvidenceOperation.pending(
                UUID.randomUUID(), "tenant-a", "key-1", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "supporting-document",
                UUID.randomUUID(), UUID.randomUUID(), NOW);
    }

    private static ClaimedConsultancyEvidenceOperation claim(ConsultancyEvidenceOperation operation) {
        return new ClaimedConsultancyEvidenceOperation(
                operation, "worker-1", UUID.randomUUID(), NOW.plusSeconds(60));
    }

    private static final class FakeAuthorization implements ConsultancyEvidenceAuthorization {
        boolean deliverableChecked;
        boolean documentChecked;
        boolean denyDeliverable;

        @Override
        public Uni<Void> requireDeliverableAccess(DeliverableAccess request) {
            deliverableChecked = true;
            if (denyDeliverable) {
                return Uni.createFrom().failure(new ConsultancyEvidenceAccessDeniedException("not a deliverable party"));
            }
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Void> requireDocumentAccess(DocumentAccess request) {
            documentChecked = true;
            return Uni.createFrom().voidItem();
        }
    }

    private static final class FakeLinks implements ConsultancyEvidenceLinkStore {
        private final ConsultancyEvidenceOperation operation;
        boolean attached;

        private FakeLinks(ConsultancyEvidenceOperation operation) { this.operation = operation; }

        @Override
        public Uni<ConsultancyEvidenceLink> attach(String tenantId, UUID deliverableId, UUID documentId,
                UUID documentVersionId, String relationship, UUID actorId) {
            attached = true;
            return Uni.createFrom().item(new ConsultancyEvidenceLink(
                    UUID.randomUUID(), deliverableId, documentId, documentVersionId,
                    relationship, actorId, NOW));
        }

        @Override
        public Uni<List<ConsultancyEvidenceLink>> findByDeliverable(String tenantId, UUID deliverableId) {
            return Uni.createFrom().item(List.of());
        }
    }

    private static final class FakeOperationStore implements ConsultancyEvidenceOperationStore {
        boolean linked;
        boolean retryable;
        boolean terminal;

        @Override public Uni<ConsultancyEvidenceOperation> createIfAbsent(ConsultancyEvidenceOperation op) {
            return Uni.createFrom().item(op);
        }
        @Override public Uni<Optional<ConsultancyEvidenceOperation>> findByIdempotencyKey(String t, String k) {
            return Uni.createFrom().item(Optional.empty());
        }
        @Override public Uni<Optional<ConsultancyEvidenceOperation>> findById(String t, UUID id) {
            return Uni.createFrom().item(Optional.empty());
        }
        @Override public Uni<List<ClaimedConsultancyEvidenceOperation>> claimDue(
                String t, Instant n, Instant l, int limit, String worker) {
            return Uni.createFrom().item(List.of());
        }
        @Override public Uni<Optional<ClaimedConsultancyEvidenceOperation>> renewLease(
                ClaimedConsultancyEvidenceOperation c, Instant n, Instant l) {
            return Uni.createFrom().item(Optional.of(c));
        }
        @Override public Uni<Boolean> markLinked(ClaimedConsultancyEvidenceOperation c, Instant n) {
            linked = true; return Uni.createFrom().item(true);
        }
        @Override public Uni<Boolean> markRetryableFailure(
                ClaimedConsultancyEvidenceOperation c, String r, Instant retryAt, Instant n) {
            retryable = true; return Uni.createFrom().item(true);
        }
        @Override public Uni<Boolean> markTerminalFailure(
                ClaimedConsultancyEvidenceOperation c, String r, Instant n) {
            terminal = true; return Uni.createFrom().item(true);
        }
    }
}
