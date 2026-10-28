package tech.kayys.numou.automotiveparts.spi;

import tech.kayys.numou.automotiveparts.domain.AutomotivePartProfile;
import tech.kayys.numou.automotiveparts.domain.PartFitment;
import tech.kayys.numou.automotiveparts.domain.PartReference;
import tech.kayys.numou.automotiveparts.domain.PartSubstitution;
import tech.kayys.numou.automotiveparts.domain.VehicleSpecification;
import tech.kayys.numou.product.domain.product.ProductId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped persistence port. Implementations must enforce reference uniqueness
 * atomically, including concurrent writes, using the reference namespace
 * (tenant, type, issuer, market, normalized value). A preflight lookup alone is not
 * sufficient. No persistence entity or database schema is part of this contract.
 */
public interface AutomotivePartRepository {
    Optional<AutomotivePartProfile> findProfile(String tenantId, ProductId productId);

    /** Resolve a reference within the tenant and namespace; never search globally. */
    Optional<AutomotivePartProfile> findByReference(String tenantId, PartReference reference);

    /**
     * Persist a profile and all of its references as one atomic operation.
     * Implementations must reject a reference already owned by another product in
     * the same namespace and must not leave a partially written profile.
     */
    void saveProfile(AutomotivePartProfile profile);

    /**
     * Return candidate fitment claims for the tenant and supplied vehicle/date.
     * Implementations may over-select, but must not omit any record for which
     * VehicleFitment.matches(vehicle, asOf) is true. The application layer performs
     * the authoritative match before presenting results.
     */
    List<PartFitment> findFitmentCandidates(String tenantId, VehicleSpecification vehicle, LocalDate asOf);

    /** Persist a directed substitution policy. Replaying the same claim must be idempotent. */
    void saveSubstitution(PartSubstitution substitution);

    /** Find substitutions effective on the requested date, scoped to one tenant and source product. */
    List<PartSubstitution> findSubstitutions(String tenantId, ProductId sourceProductId, LocalDate asOf);

    /** Persist the fitment claim for a product. Tenant isolation is mandatory. */
    void saveFitment(PartFitment fitment);
}