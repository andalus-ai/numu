package tech.kayys.numou.scope.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BreakdownRevisionTest {

    @Test
    void acceptsAndCanonicallyOrdersACompleteTenantScopedTree() {
        var structureId = BreakdownStructureId.generate();
        var root = BreakdownNode.create(structureId, null, "ROOT", "Root", "GROUP", 0);
        var second = BreakdownNode.create(structureId, root.id(), "B", "Second", "ITEM", 1);
        var first = BreakdownNode.create(structureId, root.id(), "A", "First", "ITEM", 0);

        var revision = revision(structureId, List.of(second, root, first));

        assertEquals(List.of(root, first, second), revision.nodes());
        assertEquals(1, revision.revisionNumber());
    }

    @Test
    void rejectsEmptyRevision() {
        assertThrows(IllegalArgumentException.class,
                () -> revision(BreakdownStructureId.generate(), List.of()));
    }

    @Test
    void rejectsNodesFromAnotherStructure() {
        var structureId = BreakdownStructureId.generate();
        var foreign = BreakdownNode.create(
                BreakdownStructureId.generate(), null, "ROOT", "Root", "GROUP", 0);

        assertThrows(IllegalArgumentException.class,
                () -> revision(structureId, List.of(foreign)));
    }

    @Test
    void rejectsMultipleRoots() {
        var structureId = BreakdownStructureId.generate();
        var root1 = BreakdownNode.create(structureId, null, "ROOT-1", "Root 1", "GROUP", 0);
        var root2 = BreakdownNode.create(structureId, null, "ROOT-2", "Root 2", "GROUP", 1);

        assertThrows(IllegalArgumentException.class,
                () -> revision(structureId, List.of(root1, root2)));
    }

    @Test
    void rejectsDuplicateSiblingPositions() {
        var structureId = BreakdownStructureId.generate();
        var root = BreakdownNode.create(structureId, null, "ROOT", "Root", "GROUP", 0);
        var child1 = BreakdownNode.create(structureId, root.id(), "A", "A", "ITEM", 0);
        var child2 = BreakdownNode.create(structureId, root.id(), "B", "B", "ITEM", 0);

        assertThrows(IllegalArgumentException.class,
                () -> revision(structureId, List.of(root, child1, child2)));
    }

    @Test
    void rejectsMalformedContentHashAndNonPositiveRevisionNumber() {
        var structureId = BreakdownStructureId.generate();
        var root = BreakdownNode.create(structureId, null, "ROOT", "Root", "GROUP", 0);

        assertThrows(IllegalArgumentException.class,
                () -> new BreakdownRevision(TenantId.generate(), structureId,
                        BreakdownRevisionId.generate(), 0, "sha256:" + "a".repeat(64),
                        Instant.now(), "actor-1", List.of(root)));
        assertThrows(IllegalArgumentException.class,
                () -> new BreakdownRevision(TenantId.generate(), structureId,
                        BreakdownRevisionId.generate(), 1, "not-a-hash",
                        Instant.now(), "actor-1", List.of(root)));
    }

    private static BreakdownRevision revision(
            BreakdownStructureId structureId, List<BreakdownNode> nodes) {
        return new BreakdownRevision(
                TenantId.generate(), structureId, BreakdownRevisionId.generate(),
                1, "sha256:" + "a".repeat(64), Instant.parse("2026-11-06T00:00:00Z"),
                "actor-1", nodes);
    }
}
