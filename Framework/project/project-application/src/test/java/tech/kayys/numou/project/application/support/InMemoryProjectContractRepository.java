package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.commercial.ProjectContract;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.spi.port.ProjectContractRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class InMemoryProjectContractRepository implements ProjectContractRepository {

    private final Map<ProjectContractId, ProjectContract> contractsById = new LinkedHashMap<>();
    private final Map<ProjectId, ProjectContractId> idsByProjectId = new LinkedHashMap<>();

    @Override
    public CompletionStage<ProjectContract> save(ProjectContract contract) {
        contractsById.put(contract.id(), contract);
        idsByProjectId.put(contract.projectId(), contract.id());
        return CompletableFuture.completedFuture(contract);
    }

    @Override
    public CompletionStage<Optional<ProjectContract>> findById(ProjectContractId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(contractsById.get(id)));
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
