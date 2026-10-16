package tech.kayys.numou.foundation.application.page;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageTest {

    @Test
    void computesTotalPagesAndHasNext() {
        var page = Page.of(List.of("a", "b"), 25, PageRequest.of(0, 10));

        assertEquals(3, page.totalPages());
        assertTrue(page.hasNext());
    }

    @Test
    void detectsLastPage() {
        var page = Page.of(List.of("a"), 21, PageRequest.of(2, 10));

        assertFalse(page.hasNext());
    }

    @Test
    void rejectsInvalidPageRequest() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 0));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 1_000));
    }

    @Test
    void emptyPageHasNoContent() {
        var page = Page.empty(PageRequest.first(20));

        assertTrue(page.isEmpty());
        assertEquals(0, page.totalPages());
    }

}
