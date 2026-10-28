package tech.kayys.numou.automotiveparts.application;

import tech.kayys.numou.automotiveparts.domain.AutomotivePartProfile;
import tech.kayys.numou.automotiveparts.domain.PartReference;
import tech.kayys.numou.automotiveparts.spi.AutomotivePartRepository;

import java.util.Objects;
import java.util.Optional;

/** Resolves a part reference inside an explicit tenant boundary. */
public final class FindAutomotivePartByReference {
    public record Query(String tenantId, PartReference reference) {
        public Query {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            tenantId = tenantId.trim();
            Objects.requireNonNull(reference, "reference is required");
        }
    }

    private final AutomotivePartRepository repository;

    public FindAutomotivePartByReference(AutomotivePartRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
    }

    public Optional<AutomotivePartProfile> execute(Query query) {
        Objects.requireNonNull(query, "query is required");
        return repository.findByReference(query.tenantId(), query.reference())
                .filter(profile -> profile.tenantId().equals(query.tenantId()));
    }
}