package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.commercial.ProjectClaim;
import tech.kayys.numou.project.domain.commercial.ProjectClaimId;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.spi.port.ProjectClaimRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProjectClaimRepository implements ProjectClaimRepository {

    private final Map<ProjectClaimId, ProjectClaim> claimsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProjectClaim> save(ProjectClaim claim) {
        Objects.requireNonNull(claim, "claim cannot be null");
        claimsById.put(claim.id(), claim);
        return CompletableFuture.completedFuture(claim);
    }

    @Override
    public CompletionStage<Optional<ProjectClaim>> findById(ProjectClaimId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(claimsById.get(id))
        );
    }

    @Override
    public CompletionStage<List<ProjectClaim>> findByProjectId(ProjectId projectId) {
        Objects.requireNonNull(projectId, "projectId cannot be null");

        var list = claimsById.values().stream()
                .filter(c -> c.projectId().equals(projectId))
                .toList();

        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<Boolean> existsById(ProjectClaimId id) {
        return CompletableFuture.completedFuture(claimsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ProjectClaim claim) {
        Objects.requireNonNull(claim, "claim cannot be null");
        claimsById.remove(claim.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectClaimId id) {
        claimsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
