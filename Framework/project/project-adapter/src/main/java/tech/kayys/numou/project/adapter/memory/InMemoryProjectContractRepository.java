package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.commercial.ProjectContract;
import tech.kayys.numou.project.domain.commercial.ProjectContractId;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.spi.port.ProjectContractRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProjectContractRepository implements ProjectContractRepository {

    private final Map<ProjectContractId, ProjectContract> contractsById = new ConcurrentHashMap<>();
    private final Map<ProjectId, ProjectContractId> idsByProjectId = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProjectContract> save(ProjectContract contract) {
        Objects.requireNonNull(contract, "contract cannot be null");

        contractsById.put(contract.id(), contract);
        idsByProjectId.put(contract.projectId(), contract.id());

        return CompletableFuture.completedFuture(contract);
    }

    @Override
    public CompletionStage<Optional<ProjectContract>> findById(ProjectContractId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(contractsById.get(id))
        );
    }

    @Override
    public CompletionStage<Optional<ProjectContract>> findByProjectId(ProjectId projectId) {
        var id = idsByProjectId.get(projectId);
        return CompletableFuture.completedFuture(
                id == null ? Optional.empty() : Optional.ofNullable(contractsById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ProjectContractId id) {
        return CompletableFuture.completedFuture(contractsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ProjectContract contract) {
        Objects.requireNonNull(contract, "contract cannot be null");

        contractsById.remove(contract.id());
        idsByProjectId.remove(contract.projectId());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectContractId id) {
        var existing = contractsById.remove(id);
        if (existing != null) {
            idsByProjectId.remove(existing.projectId());
        }
        return CompletableFuture.completedFuture(null);
    }
}
