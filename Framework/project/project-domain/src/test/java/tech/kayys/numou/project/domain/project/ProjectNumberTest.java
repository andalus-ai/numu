package tech.kayys.syirkah.project.domain.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ProjectNumber value object")
class ProjectNumberTest {

    @Test
    @DisplayName("trims the value and compares by value")
    void trimsAndComparesByValue() {
        var number = ProjectNumber.of("  PRJ-2026-001  ");

        assertEquals("PRJ-2026-001", number.value());
        assertEquals(ProjectNumber.of("PRJ-2026-001"), number);
        assertEquals("PRJ-2026-001", number.toString());
    }

    @Test
    @DisplayName("rejects null, blank and over-long numbers")
    void rejectsInvalidNumbers() {
        assertThrows(
                NullPointerException.class,
                () -> ProjectNumber.of(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectNumber.of("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectNumber.of("P".repeat(51))
        );
    }
}
