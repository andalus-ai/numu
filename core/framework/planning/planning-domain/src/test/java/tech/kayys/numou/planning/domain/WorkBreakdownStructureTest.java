package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkBreakdownStructureTest {
    @Test
    void traversesDescendantsInStableDepthFirstOrder() {
        UUID root = UUID.randomUUID();
        UUID phase = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var structure = WorkBreakdownStructure.create(UUID.randomUUID(), "PROJECT", List.of(
                new WorkBreakdownNode(root, null, "ROOT", "Project", 0),
                new WorkBreakdownNode(phase, root, "PHASE", "Phase", 0),
                new WorkBreakdownNode(second, phase, "B", "Second", 1),
                new WorkBreakdownNode(first, phase, "A", "First", 0)));

        assertEquals(List.of(phase, first, second),
                structure.descendantsOf(root).stream().map(WorkBreakdownNode::id).toList());
        assertEquals(List.of(first, second),
                structure.childrenOf(phase).stream().map(WorkBreakdownNode::id).toList());
    }

    @Test
    void rejectsMissingParentsAndMultipleRoots() {
        UUID missing = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> WorkBreakdownStructure.create(UUID.randomUUID(), "P",
                List.of(new WorkBreakdownNode(UUID.randomUUID(), missing, "A", "A", 0))));
        assertThrows(IllegalArgumentException.class, () -> WorkBreakdownStructure.create(UUID.randomUUID(), "P",
                List.of(new WorkBreakdownNode(UUID.randomUUID(), null, "A", "A", 0),
                        new WorkBreakdownNode(UUID.randomUUID(), null, "B", "B", 1))));
    }

    @Test
    void rejectsDuplicateSiblingCodesAndPositions() {
        UUID root = UUID.randomUUID();
        var one = new WorkBreakdownNode(UUID.randomUUID(), root, "TASK", "One", 0);
        var duplicateCode = new WorkBreakdownNode(UUID.randomUUID(), root, "task", "Two", 1);
        assertThrows(IllegalArgumentException.class, () -> WorkBreakdownStructure.create(UUID.randomUUID(), "P",
                List.of(new WorkBreakdownNode(root, null, "ROOT", "Root", 0), one, duplicateCode)));

        var duplicatePosition = new WorkBreakdownNode(UUID.randomUUID(), root, "OTHER", "Other", 0);
        assertThrows(IllegalArgumentException.class, () -> WorkBreakdownStructure.create(UUID.randomUUID(), "P",
                List.of(new WorkBreakdownNode(root, null, "ROOT", "Root", 0), one, duplicatePosition)));
    }

    @Test
    void rejectsSelfParentAndUnknownNodeLookups() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new WorkBreakdownNode(id, id, "A", "A", 0));
        var structure = WorkBreakdownStructure.create(UUID.randomUUID(), "P",
                List.of(new WorkBreakdownNode(id, null, "ROOT", "Root", 0)));
        assertThrows(IllegalArgumentException.class, () -> structure.node(UUID.randomUUID()));
    }
}
