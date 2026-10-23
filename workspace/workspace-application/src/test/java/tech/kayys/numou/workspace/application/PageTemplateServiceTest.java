package tech.kayys.numou.workspace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workspace.domain.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PageTemplateServiceTest {
    @Test void templateInstantiationUsesFreshBlockIdsAndPreservesContent() {
        UUID tenant = UUID.randomUUID(), workspace = UUID.randomUUID(), actor = UUID.randomUUID();
        PageTemplate template = PageTemplate.create(tenant, workspace, actor, "Meeting", "Meeting notes",
                List.of(PageBlock.text(BlockType.HEADING, "Agenda", 0),
                        PageBlock.text(BlockType.TODO, "Capture actions", 1)), Instant.EPOCH);
        List<PageBlock> first = template.instantiateBlocks();
        List<PageBlock> second = template.instantiateBlocks();
        assertEquals(List.of("Agenda", "Capture actions"), first.stream().map(PageBlock::text).toList());
        assertEquals(List.of(0, 1), first.stream().map(PageBlock::position).toList());
        assertNotEquals(first.get(0).id(), second.get(0).id());
        assertNotEquals(first.get(0).id(), template.blocks().get(0).id());
    }

    @Test void rejectsDuplicatePositionsAndInvalidNames() {
        UUID t = UUID.randomUUID(), w = UUID.randomUUID(), a = UUID.randomUUID();
        PageBlock one = PageBlock.text(BlockType.PARAGRAPH, "one", 0);
        PageBlock two = PageBlock.text(BlockType.PARAGRAPH, "two", 0);
        assertThrows(IllegalArgumentException.class,
                () -> PageTemplate.create(t, w, a, "Template", "Title", List.of(one, two), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> PageTemplate.create(t, w, a, " ", "Title", List.of(), Instant.EPOCH));
    }
}
