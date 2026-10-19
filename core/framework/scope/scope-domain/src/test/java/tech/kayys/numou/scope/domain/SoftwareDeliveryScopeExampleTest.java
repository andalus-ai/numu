package tech.kayys.numou.scope.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executable example proving the neutral scope model can represent software
 * delivery without importing software-delivery or construction domain types.
 */
class SoftwareDeliveryScopeExampleTest {

    @Test
    void modelsAnEpicStoryAndBugHierarchyUsingTheNeutralScopeContract() {
        var projectReference = "software-project:" + UUID.randomUUID();
        var structure = BreakdownStructure.create(
                BreakdownStructureType.of("SOFTWARE_BACKLOG"),
                "CHECKOUT",
                "Checkout delivery scope",
                projectReference);

        var epic = BreakdownNode.create(
                structure.id(), null, "EPIC-1", "Checkout experience", "EPIC", 0);
        var story = BreakdownNode.create(
                structure.id(), epic.id(), "STORY-1", "Support card payment", "STORY", 0);
        var bug = BreakdownNode.create(
                structure.id(), story.id(), "BUG-1", "Handle declined cards", "BUG", 0);

        var violations = BreakdownTreeValidator.validate(List.of(epic, story, bug));

        assertTrue(violations.isEmpty(), () -> "Expected valid software backlog: " + violations);
        assertEquals(structure.id(), epic.structureId());
        assertEquals(epic.id(), story.parentId());
        assertEquals(story.id(), bug.parentId());
        assertEquals("SOFTWARE_BACKLOG", structure.type().code());
        assertEquals(projectReference, structure.ownerReference());
    }

    @Test
    void rejectsDuplicateSoftwareWorkItemCodesAmongSiblings() {
        var structure = BreakdownStructure.create(
                BreakdownStructureType.of("SOFTWARE_BACKLOG"),
                "API",
                "API delivery scope",
                "software-project:api");

        var epic = BreakdownNode.create(
                structure.id(), null, "EPIC-1", "API", "EPIC", 0);
        var firstStory = BreakdownNode.create(
                structure.id(), epic.id(), "STORY-1", "Create endpoint", "STORY", 0);
        var duplicateStory = BreakdownNode.create(
                structure.id(), epic.id(), "story-1", "Update endpoint", "STORY", 1);

        var violations = BreakdownTreeValidator.validate(
                List.of(epic, firstStory, duplicateStory));

        assertEquals(1, violations.stream()
                .filter(violation -> violation.code()
                        == BreakdownTreeValidator.Code.DUPLICATE_SIBLING_CODE)
                .count());
    }
}
