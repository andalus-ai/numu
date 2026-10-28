package tech.kayys.numou.automotiveparts.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.automotiveparts.domain.*;
import tech.kayys.numou.automotiveparts.spi.AutomotivePartRepository;
import tech.kayys.numou.product.domain.product.ProductId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AutomotivePartsUseCasesTest {
    @Test
    void referenceLookupAlwaysPassesExplicitTenantAndFiltersUnexpectedCrossTenantResult() {
        var repository = new FakeRepository();
        var productId = ProductId.of(UUID.randomUUID());
        repository.profile = profile("tenant-a", productId);
        var lookup = new FindAutomotivePartByReference(repository);

        assertTrue(lookup.execute(new FindAutomotivePartByReference.Query(
                "tenant-a", new PartReference(PartReferenceType.OEM, "OEM-1"))).isPresent());
        assertTrue(lookup.execute(new FindAutomotivePartByReference.Query(
                "tenant-b", new PartReference(PartReferenceType.OEM, "OEM-1"))).isEmpty());
        assertEquals("tenant-b", repository.lastLookupTenant);
    }

    @Test
    void compatibleSearchFiltersNonMatchingAndCrossTenantClaimsAndGroupsEvidenceByProduct() {
        var repository = new FakeRepository();
        var productId = ProductId.of(UUID.randomUUID());
        repository.candidates.add(new PartFitment("tenant-a", productId,
                new VehicleFitment("tenant-a", "ID", "TOYOTA", "AVANZA", null, 2020, 2024,
                        null, null, "OEM_CATALOG", "row-1", LocalDate.of(2024, 1, 1), null)));
        repository.candidates.add(new PartFitment("tenant-a", ProductId.of(UUID.randomUUID()),
                new VehicleFitment("tenant-a", "ID", "TOYOTA", "AVANZA", null, 2010, 2015,
                        null, null, "OLD_CATALOG", "row-old", LocalDate.of(2010, 1, 1), null)));
        repository.candidates.add(new PartFitment("tenant-b", ProductId.of(UUID.randomUUID()),
                new VehicleFitment("tenant-b", "ID", "TOYOTA", "AVANZA", null, 2020, 2024,
                        null, null, "OTHER_TENANT", "row-2", LocalDate.of(2024, 1, 1), null)));

        var search = new SearchCompatibleParts(repository);
        var results = search.execute(new SearchCompatibleParts.Query("tenant-a",
                new VehicleSpecification("ID", "Toyota", "Avanza", null, 2022, null, null),
                LocalDate.of(2025, 1, 1)));

        assertEquals(1, results.size());
        assertEquals(productId, results.getFirst().productId());
        assertEquals(1, results.getFirst().matchingEvidence().size());
    }

    @Test
    void registrationDelegatesAtomicUniquenessToRepositoryAndRejectsNullInput() {
        var repository = new FakeRepository();
        var useCase = new RegisterAutomotivePart(repository);
        var profile = profile("tenant-a", ProductId.of(UUID.randomUUID()));
        useCase.execute(profile);
        assertEquals(profile, repository.savedProfile);
        assertThrows(NullPointerException.class, () -> useCase.execute(null));
    }

    private static AutomotivePartProfile profile(String tenant, ProductId id) {
        return new AutomotivePartProfile(tenant, id,
                AutomotivePartProfile.PartKind.REPLACEMENT_PART, "Oil filter",
                List.of(new PartReference(PartReferenceType.OEM, "OEM-1")));
    }

    private static final class FakeRepository implements AutomotivePartRepository {
        private AutomotivePartProfile profile;
        private AutomotivePartProfile savedProfile;
        private String lastLookupTenant;
        private final List<PartFitment> candidates = new ArrayList<>();

        @Override public Optional<AutomotivePartProfile> findProfile(String tenantId, ProductId productId) {
            return profile != null && profile.tenantId().equals(tenantId) && profile.productId().equals(productId)
                    ? Optional.of(profile) : Optional.empty();
        }
        @Override public Optional<AutomotivePartProfile> findByReference(String tenantId, PartReference reference) {
            lastLookupTenant = tenantId;
            return Optional.ofNullable(profile);
        }
        @Override public void saveProfile(AutomotivePartProfile profile) { savedProfile = profile; }
        @Override public List<PartFitment> findFitmentCandidates(String tenantId, VehicleSpecification vehicle, LocalDate asOf) {
            return List.copyOf(candidates);
        }
        @Override public void saveFitment(PartFitment fitment) { candidates.add(fitment); }
    }
}