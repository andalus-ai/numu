package tech.kayys.numou.automotiveparts.application;

import tech.kayys.numou.automotiveparts.domain.PartFitment;
import tech.kayys.numou.automotiveparts.domain.VehicleSpecification;
import tech.kayys.numou.automotiveparts.spi.AutomotivePartRepository;
import tech.kayys.numou.product.domain.product.ProductId;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Searches positive fitment claims; it does not infer compatibility from catalog similarity. */
public final class SearchCompatibleParts {
    public record Query(String tenantId, VehicleSpecification vehicle, LocalDate asOf) {
        public Query {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            tenantId = tenantId.trim();
            Objects.requireNonNull(vehicle, "vehicle is required");
            Objects.requireNonNull(asOf, "asOf is required");
        }
    }

    public record Result(ProductId productId, List<PartFitment> matchingEvidence) {
        public Result {
            Objects.requireNonNull(productId, "productId is required");
            matchingEvidence = List.copyOf(matchingEvidence);
            if (matchingEvidence.isEmpty()) {
                throw new IllegalArgumentException("At least one matching fitment claim is required");
            }
            if (matchingEvidence.stream().anyMatch(e -> !productId.equals(e.productId()))) {
                throw new IllegalArgumentException("All evidence must refer to the result product");
            }
        }
    }

    private final AutomotivePartRepository repository;

    public SearchCompatibleParts(AutomotivePartRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
    }

    public List<Result> execute(Query query) {
        Objects.requireNonNull(query, "query is required");
        List<PartFitment> candidates = repository.findFitmentCandidates(
                query.tenantId(), query.vehicle(), query.asOf());
        if (candidates == null) {
            throw new IllegalStateException("Repository returned null fitment candidates");
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.tenantId().equals(query.tenantId()))
                .filter(candidate -> candidate.fitment().matches(query.vehicle(), query.asOf()))
                .collect(java.util.stream.Collectors.groupingBy(
                        PartFitment::productId,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().value().toString()))
                .map(entry -> new Result(entry.getKey(), entry.getValue()))
                .toList();
    }
}