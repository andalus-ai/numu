package tech.kayys.numou.construction.application.wbs.handler;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.construction.application.wbs.command.CreateWbsNodeCommand;
import tech.kayys.numou.construction.domain.wbs.WbsNode;
import tech.kayys.numou.construction.domain.wbs.WbsNodeId;
import tech.kayys.numou.construction.domain.wbs.WbsNodeType;
import tech.kayys.numou.construction.spi.wbs.WbsNodeRepository;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.scope.domain.BreakdownStructureId;
import tech.kayys.numou.scope.spi.BreakdownStructureWriteLock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateWbsNodeHandlerTest {

    @Test
    void createsNodeAndPublishesEventWhenCandidateTreeIsValid() {
        var projectId = UUID.randomUUID();
        var repository = new InMemoryWbsNodeRepository();
        var publisher = new RecordingEventPublisher();
        var handler = new CreateWbsNodeHandler(repository, publisher, new TestWriteLock());

        var created = handler.handle(new CreateWbsNodeCommand(
                projectId, null, "1", "Project scope", WbsNodeType.PROJECT))
                .await().indefinitely();

        assertEquals(projectId, created.projectId());
        assertEquals(1, repository.saved.size());
        assertEquals(1, publisher.published.size());
        assertEquals("construction.wbs-node-created", publisher.published.getFirst().eventType());
    }

    @Test
    void rejectsDuplicateSiblingCodesIgnoringCaseBeforePersistenceOrPublication() {
        var projectId = UUID.randomUUID();
        var repository = new InMemoryWbsNodeRepository();
        repository.nodes.add(WbsNode.create(
                projectId, null, "WBS-1", "Existing root", WbsNodeType.PROJECT));
        var publisher = new RecordingEventPublisher();
        var handler = new CreateWbsNodeHandler(repository, publisher, new TestWriteLock());

        var failure = assertThrows(
                RuntimeException.class,
                () -> handler.handle(new CreateWbsNodeCommand(
                        projectId, null, "wbs-1", "Duplicate root", WbsNodeType.PHASE))
                        .await().indefinitely());

        assertTrue(rootCause(failure).getMessage().contains("DUPLICATE_SIBLING_CODE"));
        assertTrue(repository.saved.isEmpty(), "invalid WBS must not be persisted");
        assertTrue(publisher.published.isEmpty(), "invalid WBS must not publish events");
    }

    @Test
    void rejectsParentThatDoesNotExistInProjectsWbs() {
        var projectId = UUID.randomUUID();
        var repository = new InMemoryWbsNodeRepository();
        var publisher = new RecordingEventPublisher();
        var handler = new CreateWbsNodeHandler(repository, publisher, new TestWriteLock());

        var failure = assertThrows(
                RuntimeException.class,
                () -> handler.handle(new CreateWbsNodeCommand(
                        projectId, UUID.randomUUID(), "1.1", "Orphan", WbsNodeType.ACTIVITY))
                        .await().indefinitely());

        assertTrue(rootCause(failure).getMessage().contains("MISSING_PARENT"));
        assertTrue(repository.saved.isEmpty(), "orphan WBS must not be persisted");
        assertTrue(publisher.published.isEmpty(), "orphan WBS must not publish events");
    }

    private static final class TestWriteLock implements BreakdownStructureWriteLock {
        @Override
        public <T> CompletionStage<T> withLock(
                BreakdownStructureId structureId,
                java.util.function.Supplier<CompletionStage<T>> operation) {
            try {
                return operation.get();
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
    }

    private static Throwable rootCause(Throwable failure) {
        var current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static final class RecordingEventPublisher implements EventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public Uni<Void> publish(List<DomainEvent> events) {
            published.addAll(events);
            return Uni.createFrom().voidItem();
        }
    }

    private static final class InMemoryWbsNodeRepository implements WbsNodeRepository {
        private final List<WbsNode> nodes = new ArrayList<>();
        private final List<WbsNode> saved = new ArrayList<>();

        @Override
        public CompletionStage<List<WbsNode>> findByProjectId(UUID projectId) {
            return CompletableFuture.completedFuture(nodes.stream()
                    .filter(node -> node.projectId().equals(projectId))
                    .toList());
        }

        @Override
        public CompletionStage<List<WbsNode>> findByParentNodeId(UUID parentNodeId) {
            return CompletableFuture.completedFuture(nodes.stream()
                    .filter(node -> java.util.Objects.equals(node.parentNodeId(), parentNodeId))
                    .toList());
        }

        @Override
        public CompletionStage<WbsNode> save(WbsNode aggregate) {
            saved.add(aggregate);
            nodes.add(aggregate);
            return CompletableFuture.completedFuture(aggregate);
        }

        @Override
        public CompletionStage<Optional<WbsNode>> findById(WbsNodeId id) {
            return CompletableFuture.completedFuture(nodes.stream()
                    .filter(node -> node.id().equals(id))
                    .findFirst());
        }

        @Override
        public CompletionStage<Boolean> existsById(WbsNodeId id) {
            return CompletableFuture.completedFuture(nodes.stream().anyMatch(node -> node.id().equals(id)));
        }

        @Override
        public CompletionStage<Void> delete(WbsNode aggregate) {
            nodes.remove(aggregate);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteById(WbsNodeId id) {
            nodes.removeIf(node -> node.id().equals(id));
            return CompletableFuture.completedFuture(null);
        }
    }
}
