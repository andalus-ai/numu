package tech.kayys.numou.scope.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.scope.domain.BreakdownNode;
import tech.kayys.numou.scope.domain.BreakdownNodeId;
import tech.kayys.numou.scope.domain.BreakdownStructure;
import tech.kayys.numou.scope.domain.BreakdownStructureId;
import tech.kayys.numou.scope.domain.BreakdownStructureType;
import tech.kayys.numou.scope.spi.BreakdownNodeRepository;
import tech.kayys.numou.scope.spi.BreakdownStructureRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class SaveBreakdownNodeTest {
    @Test
    void savesCandidateWhenCompleteTreeIsValid() {
        var structure = structure();
        var root = BreakdownNode.create(structure.id(), null, "EPIC-1", "Epic", "EPIC", 0);
        var repository = new FakeNodes(List.of(root));
        var useCase = new SaveBreakdownNode(new FakeStructures(structure), repository);
        var child = BreakdownNode.create(structure.id(), root.id(), "STORY-1", "Story", "STORY", 0);

        var saved = useCase.execute(child).toCompletableFuture().join();

        assertEquals(child, saved);
        assertEquals(2, repository.saved.size());
    }

    @Test
    void rejectsDuplicateSiblingCodesBeforePersistence() {
        var structure = structure();
        var existing = BreakdownNode.create(structure.id(), null, "EPIC-1", "First", "EPIC", 0);
        var repository = new FakeNodes(List.of(existing));
        var useCase = new SaveBreakdownNode(new FakeStructures(structure), repository);
        var duplicate = BreakdownNode.create(structure.id(), null, "epic-1", "Duplicate", "EPIC", 1);

        var error = assertThrows(CompletionException.class,
                () -> useCase.execute(duplicate).toCompletableFuture().join());

        assertInstanceOf(SaveBreakdownNode.InvalidBreakdownTreeException.class, error.getCause());
        assertTrue(repository.saved.isEmpty());
    }

    @Test
    void rejectsMissingParentBeforePersistence() {
        var structure = structure();
        var repository = new FakeNodes(List.of());
        var useCase = new SaveBreakdownNode(new FakeStructures(structure), repository);
        var orphan = BreakdownNode.create(
                structure.id(), BreakdownNodeId.generate(), "STORY-1", "Orphan", "STORY", 0);

        var error = assertThrows(CompletionException.class,
                () -> useCase.execute(orphan).toCompletableFuture().join());

        assertInstanceOf(SaveBreakdownNode.InvalidBreakdownTreeException.class, error.getCause());
        assertTrue(repository.saved.isEmpty());
    }

    @Test
    void rejectsUnknownStructureBeforeReadingOrSavingNodes() {
        var repository = new FakeNodes(List.of());
        var useCase = new SaveBreakdownNode(new FakeStructures(null), repository);
        var candidate = BreakdownNode.create(
                BreakdownStructureId.generate(), null, "ROOT", "Root", "ROOT", 0);

        assertThrows(CompletionException.class,
                () -> useCase.execute(candidate).toCompletableFuture().join());
        assertTrue(repository.saved.isEmpty());
        assertEquals(0, repository.reads);
    }

    private static BreakdownStructure structure() {
        return BreakdownStructure.create(
                BreakdownStructureType.of("WBS"), "PROJECT-WBS", "Project WBS", "project-123");
    }

    private static final class FakeStructures implements BreakdownStructureRepository {
        private final BreakdownStructure structure;

        private FakeStructures(BreakdownStructure structure) {
            this.structure = structure;
        }

        @Override
        public java.util.concurrent.CompletionStage<Optional<BreakdownStructure>> findById(
                BreakdownStructureId id) {
            return CompletableFuture.completedFuture(
                    structure != null && structure.id().equals(id) ? Optional.of(structure) : Optional.empty());
        }

        @Override
        public java.util.concurrent.CompletionStage<Optional<BreakdownStructure>> findByOwnerReferenceAndCode(
                String ownerReference, String code) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public java.util.concurrent.CompletionStage<BreakdownStructure> save(BreakdownStructure value) {
            return CompletableFuture.completedFuture(value);
        }
    }

    private static final class FakeNodes implements BreakdownNodeRepository {
        private final List<BreakdownNode> current;
        private final List<BreakdownNode> saved = new ArrayList<>();
        private int reads;

        private FakeNodes(List<BreakdownNode> current) {
            this.current = List.copyOf(current);
        }

        @Override
        public java.util.concurrent.CompletionStage<Optional<BreakdownNode>> findById(BreakdownNodeId id) {
            return CompletableFuture.completedFuture(current.stream()
                    .filter(node -> node.id().equals(id)).findFirst());
        }

        @Override
        public java.util.concurrent.CompletionStage<List<BreakdownNode>> findByStructureId(
                BreakdownStructureId structureId) {
            reads++;
            return CompletableFuture.completedFuture(current.stream()
                    .filter(node -> node.structureId().equals(structureId)).toList());
        }

        @Override
        public java.util.concurrent.CompletionStage<List<BreakdownNode>> findByParentId(
                BreakdownStructureId structureId, BreakdownNodeId parentId) {
            return CompletableFuture.completedFuture(current.stream()
                    .filter(node -> node.structureId().equals(structureId)
                            && java.util.Objects.equals(node.parentId(), parentId)).toList());
        }

        @Override
        public java.util.concurrent.CompletionStage<BreakdownNode> save(BreakdownNode node) {
            saved.add(node);
            return CompletableFuture.completedFuture(node);
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> delete(BreakdownNodeId id) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
