package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.project.ProjectCostEntry;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.project.ProjectRevenueEntry;
import tech.kayys.numou.project.spi.port.ProjectFinanceStorePort;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory, append-only adapter for project cost and revenue entries.
 */
public final class InMemoryProjectFinanceStore
        implements ProjectFinanceStorePort {

    private final CopyOnWriteArrayList<ProjectCostEntry> costs =
            new CopyOnWriteArrayList<>();

    private final CopyOnWriteArrayList<ProjectRevenueEntry> revenues =
            new CopyOnWriteArrayList<>();

    @Override
    public List<ProjectCostEntry> findCosts(ProjectId projectId) {
        Objects.requireNonNull(projectId, "projectId cannot be null");

        return costs.stream()
                .filter(entry -> entry.projectId().equals(projectId))
                .toList();
    }

    @Override
    public void saveCost(ProjectCostEntry entry) {
        costs.add(Objects.requireNonNull(entry, "entry cannot be null"));
    }

    @Override
    public List<ProjectRevenueEntry> findRevenues(ProjectId projectId) {
        Objects.requireNonNull(projectId, "projectId cannot be null");

        return revenues.stream()
                .filter(entry -> entry.projectId().equals(projectId))
                .toList();
    }

    @Override
    public void saveRevenue(ProjectRevenueEntry entry) {
        revenues.add(Objects.requireNonNull(entry, "entry cannot be null"));
    }
}
