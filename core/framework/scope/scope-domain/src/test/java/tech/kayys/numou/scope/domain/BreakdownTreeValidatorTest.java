package tech.kayys.numou.scope.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BreakdownTreeValidatorTest {
    @Test
    void acceptsValidTreeAndUniqueCodesUnderDifferentParents() {
        var structure = BreakdownStructureId.generate();
        var rootA = BreakdownNode.create(structure, null, "A", "Root A", "GROUP", 0);
        var rootB = BreakdownNode.create(structure, null, "B", "Root B", "GROUP", 1);
        var childA = BreakdownNode.create(structure, rootA.id(), "1", "Child", "ITEM", 0);
        var childB = BreakdownNode.create(structure, rootB.id(), "1", "Child", "ITEM", 0);

        assertTrue(BreakdownTreeValidator.validate(List.of(rootA, rootB, childA, childB)).isEmpty());
    }

    @Test
    void reportsMissingParentAndDuplicateSiblingCodesIgnoringCase() {
        var structure = BreakdownStructureId.generate();
        var missingParent = BreakdownNodeId.generate();
        var first = BreakdownNode.create(structure, null, "root", "First", "GROUP", 0);
        var duplicate = BreakdownNode.create(structure, null, "ROOT", "Duplicate", "GROUP", 1);
        var orphan = BreakdownNode.create(structure, missingParent, "child", "Orphan", "ITEM", 0);

        var codes = BreakdownTreeValidator.validate(List.of(first, duplicate, orphan)).stream()
                .map(BreakdownTreeValidator.Violation::code).toList();

        assertTrue(codes.contains(BreakdownTreeValidator.Code.MISSING_PARENT));
        assertTrue(codes.contains(BreakdownTreeValidator.Code.DUPLICATE_SIBLING_CODE));
    }

    @Test
    void detectsCrossStructureParentAndCycles() {
        var firstStructure = BreakdownStructureId.generate();
        var secondStructure = BreakdownStructureId.generate();
        var parentId = BreakdownNodeId.generate();
        var childId = BreakdownNodeId.generate();
        var parent = new BreakdownNode(parentId, firstStructure, childId, "P", "Parent", "GROUP", 0);
        var child = new BreakdownNode(childId, secondStructure, parentId, "C", "Child", "ITEM", 0);

        var violations = BreakdownTreeValidator.validate(List.of(parent, child));
        var codes = violations.stream().map(BreakdownTreeValidator.Violation::code).toList();

        assertTrue(codes.contains(BreakdownTreeValidator.Code.PARENT_IN_DIFFERENT_STRUCTURE));
        assertTrue(codes.contains(BreakdownTreeValidator.Code.CYCLE));
    }

    @Test
    void reportsDuplicateNodeIdentity() {
        var structure = BreakdownStructureId.generate();
        var id = BreakdownNodeId.generate();
        var first = new BreakdownNode(id, structure, null, "A", "First", "GROUP", 0);
        var duplicate = new BreakdownNode(id, structure, null, "B", "Second", "GROUP", 1);

        assertTrue(BreakdownTreeValidator.validate(List.of(first, duplicate)).stream()
                .anyMatch(v -> v.code() == BreakdownTreeValidator.Code.DUPLICATE_NODE_ID));
    }
}
