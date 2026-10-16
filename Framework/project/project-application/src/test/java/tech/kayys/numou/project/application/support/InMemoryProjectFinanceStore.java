package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.project.ProjectCostEntry;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectRevenueEntry;
import tech.kayys.syirkah.project.spi.port.ProjectFinanceStorePort;

import java.util.ArrayList;
import java.util.List;

/** Test double for {@link ProjectFinanceStorePort}. */
public final class InMemoryProjectFinanceStore
        implements ProjectFinanceStorePort {

    private final List<ProjectCostEntry> costs = new ArrayList<>();
    private final List<ProjectRevenueEntry> revenues = new ArrayList<>();

    @Override
    public List<ProjectCostEntry> findCosts(ProjectId projectId) {
        return costs.stream()
                .filter(entry -> entry.projectId().equals(projectId))
                .toList();
    }

    @Override
    public void saveCost(ProjectCostEntry entry) {
        costs.add(entry);
    }

    @Override
    public List<ProjectRevenueEntry> findRevenues(ProjectId projectId) {
        return revenues.stream()
                .filter(entry -> entry.projectId().equals(projectId))
                .toList();
    }

    @Override
    public void saveRevenue(ProjectRevenueEntry entry) {
        revenues.add(entry);
    }
}
