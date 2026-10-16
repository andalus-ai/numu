package tech.kayys.numou.foundation.application.referencedata;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.referencedata.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceDataResolverTest {

    @Test
    void shouldResolveEffectiveReferenceCode() {
        ReferenceCode code = ReferenceCode.of("COUNTRY", "ID");
        ReferenceEntry entry = new ReferenceEntry(
                ReferenceEntryId.generate(),
                code,
                "Indonesia",
                "Republic of Indonesia",
                ReferenceEntryStatus.ACTIVE,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2030, 12, 31),
                "ISO 3166-1",
                1L
        );

        ReferenceDataRepository repo = new ReferenceDataRepository() {
            @Override
            public Uni<Optional<ReferenceSetDefinition>> findSetByKey(String setKey) {
                return Uni.createFrom().item(Optional.empty());
            }

            @Override
            public Uni<Optional<ReferenceEntry>> findEntryByCode(ReferenceCode c) {
                if (c.equals(code)) {
                    return Uni.createFrom().item(Optional.of(entry));
                }
                return Uni.createFrom().item(Optional.empty());
            }

            @Override
            public Uni<List<ReferenceEntry>> findEntriesBySetKey(String setKey) {
                return Uni.createFrom().item(Collections.singletonList(entry));
            }

            @Override
            public Uni<ReferenceEntry> saveEntry(ReferenceEntry e) {
                return Uni.createFrom().item(e);
            }
        };

        ReferenceDataResolver resolver = new DefaultReferenceDataResolver(repo);
        ReferenceDataResolver.ReferenceLookupContext context =
                ReferenceDataResolver.ReferenceLookupContext.of(TenantId.generate(), LocalDate.of(2026, 5, 1));

        Optional<ReferenceDataResolver.ResolvedReference> resolved =
                resolver.resolve(code, context).await().indefinitely();

        assertTrue(resolved.isPresent());
        assertEquals("Indonesia", resolved.get().label());
        assertEquals(ReferenceEntryStatus.ACTIVE, resolved.get().status());

        Boolean usable = resolver.isUsable(code, context).await().indefinitely();
        assertTrue(usable);

        // Not usable if query date is outside validity
        ReferenceDataResolver.ReferenceLookupContext pastContext =
                ReferenceDataResolver.ReferenceLookupContext.of(TenantId.generate(), LocalDate.of(2019, 1, 1));
        Boolean pastUsable = resolver.isUsable(code, pastContext).await().indefinitely();
        assertFalse(pastUsable);
    }
}
