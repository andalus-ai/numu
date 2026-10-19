package tech.kayys.numou.foundation.domain.referencedata;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceDataDomainTest {

    @Test
    void shouldCreateAndValidateReferenceCode() {
        ReferenceCode code = ReferenceCode.of("ISO_3166_1", "ID");
        assertEquals("ISO_3166_1", code.setKey());
        assertEquals("ID", code.value());
        assertEquals("ISO_3166_1:ID", code.toString());

        assertThrows(IllegalArgumentException.class, () -> ReferenceCode.of("", "ID"));
        assertThrows(IllegalArgumentException.class, () -> ReferenceCode.of("ISO_3166_1", "  "));
    }

    @Test
    void shouldEvaluateEntryLifecycleAndUsability() {
        ReferenceEntry entry = new ReferenceEntry(
                ReferenceEntryId.generate(),
                ReferenceCode.of("CURRENCY", "USD"),
                "US Dollar",
                "Official currency of the United States",
                ReferenceEntryStatus.ACTIVE,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2030, 12, 31),
                "ISO 4217",
                1L
        );

        assertTrue(entry.isEffective(LocalDate.of(2026, 6, 1)));
        assertTrue(entry.isUsable(LocalDate.of(2026, 6, 1)));
        assertFalse(entry.isEffective(LocalDate.of(2019, 12, 31)));
        assertFalse(entry.isUsable(LocalDate.of(2019, 12, 31)));

        ReferenceEntry deprecated = new ReferenceEntry(
                ReferenceEntryId.generate(),
                ReferenceCode.of("CURRENCY", "USD"),
                "US Dollar",
                null,
                ReferenceEntryStatus.DEPRECATED,
                LocalDate.of(2020, 1, 1),
                null,
                "ISO 4217",
                2L
        );

        assertTrue(deprecated.isEffective(LocalDate.of(2026, 6, 1)));
        assertFalse(deprecated.isUsable(LocalDate.of(2026, 6, 1))); // Not ACTIVE
    }

    @Test
    void shouldDefineReferenceSetWithGovernance() {
        ReferenceSetDefinition def = new ReferenceSetDefinition(
                ReferenceSetId.generate(),
                "COUNTRY_CODES",
                "ISO Standard Country Codes",
                GovernanceMode.PLATFORM_MANAGED,
                "platform",
                1L
        );

        assertEquals("COUNTRY_CODES", def.setKey());
        assertEquals(GovernanceMode.PLATFORM_MANAGED, def.governanceMode());
    }
}
