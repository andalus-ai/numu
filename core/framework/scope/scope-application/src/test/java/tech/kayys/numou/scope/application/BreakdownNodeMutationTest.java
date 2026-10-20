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
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class BreakdownNodeMutationTest {
    @Test
    void deletesLeafAndKeepsStructureScopedIdentity() {
        var structure = structure();
        var leaf = BreakdownNode.create(structure.id(), null, "STORY-1", "Story", "STORY", 0);
        var nodes = new FakeNodes(List.of(leaf));
        var useCase = new DeleteBreakdownNode(
                new FakeStructures(structure), nodes, new InProcessBreakdownStructureWriteLock());

        useCase.execute(structure.id(), leaf.id()).toCompletableFuture().join();

        assertTrue(nodes.findById(leaf.id()).toCompletableFuture().join().isEmpty());
    }

    @Test
    void refusesToDeleteNodeWithChildren() {
        var structure = structure();
        var parent = BreakdownNode.create(structure.id(), null, "EPIC-1", "Epic", "EPIC", 0);
        var child = BreakdownNode.create(structure.id(), parent.id(), "STORY-1", "Story", "STORY", 0);
        var nodes = new FakeNodes(List.of(parent, child));
        var useCase = new DeleteBreakdownNode(
                new FakeStructures(structure), nodes, new InProcessBreakdownStructureWriteLock());

        var error = assertThrows(CompletionException.class,
                () -> useCase.execute(structure.id(), parent.id()).toCompletableFuture().join());

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(2, nodes.current.size());
    }

    @Test
    void refusesToDeleteNodeThroughTheWrongStructure() {
        var owner = structure();
        var other = structure();
        var leaf = BreakdownNode.create(owner.id(), null, "ITEM-1", "Item", "ITEM", 0);
        var nodes = new FakeNodes(List.of(leaf));
        var useCase = new DeleteBreakdownNode(
                new FakeStructures(other), nodes, new InProcessBreakdownStructureWriteLock());

        var error = assertThrows(CompletionException.class,
                () -> useCase.execute(other.id(), leaf.id()).toCompletableFuture().join());

        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertEquals(1, nodes.current.size());
    }

    @Test
    void retiredStructureRejectsNodeDeletion() {
        var structure = retiredStructure();
        var leaf = BreakdownNode.create(structure.id(), null, "ITEM-1", "Item", "ITEM", 0);
        var nodes = new FakeNodes(List.of(leaf));
        var useCase = new DeleteBreakdownNode(
                new FakeStructures(structure), nodes, new InProcessBreakdownStructureWriteLock());

        var error = assertThrows(CompletionException.class,
                () -> useCase.execute(structure.id(), leaf.id()).toCompletableFuture().join());

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(1, nodes.current.size());
    }

    @Test
    void saveCannotReuseNodeIdentityOwnedByAnotherStructure() {
        var target = structure();
        var owner = structure();
        var existing = BreakdownNode.create(owner.id(), null, "ITEM-1", "Original", "ITEM", 0);
        var candidate = new BreakdownNode(
                existing.id(), target.id(), null, "ITEM-1", "Attempted move", "ITEM", 0);
        var nodes = new FakeNodes(List.of(existing));
        var useCase = new SaveBreakdownNode(
                new FakeStructures(target), nodes, new InProcessBreakdownStructureWriteLock());

        var error = assertThrows(CompletionException.class,
                () -> useCase.execute(candidate).toCompletableFuture().join());

        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("another structure"));
        assertTrue(nodes.saved.isEmpty());
        assertEquals(owner.id(), nodes.current.get(0).structureId());
    }

    @Test
    void retiredStructureRejectsNodeSave() {
        var structure = retiredStructure();
        var nodes = new FakeNodes(List.of());
        var useCase = new SaveBreakdownNode(
                new FakeStructures(structure), nodes, new InProcessBreakdownStructureWriteLock());
        var candidate = BreakdownNode.create(structure.id(), null, "ROOT", "Root", "GROUP", 0);

        var error = assertThrows(CompletionException.class,
                () -> useCase.execute(candidate).toCompletableFuture().join());

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertTrue(nodes.saved.isEmpty());
    }

    private static BreakdownStructure structure() {
        return BreakdownStructure.create(
                BreakdownStructureType.of("WBS"), "WBS-" + java.util.UUID.randomUUID(),
                "Project WBS", "owner-" + java.util.UUID.randomUUID());
    }

    private static BreakdownStructure retiredStructure() {
        var structure = structure();
        structure.activate();
        structure.retire();
        return structure;
    }

    private static final class FakeStructures implements BreakdownStructureRepository {
        private final BreakdownStructure structure;

        private FakeStructures(BreakdownStructure structure) {
            this.structure = structure;
        }

        @Override
        public CompletionStage<Optional<BreakdownStructure>> findById(BreakdownStructureId id) {
            return CompletableFuture.completedFuture(
                    structure != null && structure.id().equals(id) ? Optional.of(structure) : Optional.empty());
        }

        @Override
        public CompletionStage<Optional<BreakdownStructure>> findByOwnerReferenceAndCode(
                String ownerReference, String code) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletionStage<BreakdownStructure> save(BreakdownStructure value) {
            return CompletableFuture.completedFuture(value);
        }
    }

    private static final class FakeNodes implements BreakdownNodeRepository {
        private final List<BreakdownNode> current = new ArrayList<>();
        private final List<BreakdownNode> saved = new ArrayList<>();

        private FakeNodes(List<BreakdownNode> initial) {
            current.addAll(initial);
        }

        @Override
        public CompletionStage<Optional<BreakdownNode>> findById(BreakdownNodeId id) {
            return CompletableFuture.completedFuture(
                    current.stream().filter(node -> node.id().equals(id)).findFirst());
        }

        @Override
        public CompletionStage<List<BreakdownNode>> findByStructureId(BreakdownStructureId id) {
            return CompletableFuture.completedFuture(
                    current.stream().filter(node -> node.structureId().equals(id)).toList());
        }

        @Override
        public CompletionStage<List<BreakdownNode>> findByParentId(
                BreakdownStructureId structureId, BreakdownNodeId parentId) {
            return CompletableFuture.completedFuture(current.stream()
                    .filter(node -> node.structureId().equals(structureId)
                            && java.util.Objects.equals(node.parentId(), parentId)).toList());
        }

        @Override
        public CompletionStage<BreakdownNode> save(BreakdownNode node) {
            current.removeIf(existing -> existing.id().equals(node.id()));
            current.add(node);
            saved.add(node);
            return CompletableFuture.completedFuture(node);
        }

        @Override
        public CompletionStage<Void> delete(BreakdownNodeId id) {
            current.removeIf(node -> node.id().equals(id));
            return CompletableFuture.completedFuture(null);
        }
    }
}
