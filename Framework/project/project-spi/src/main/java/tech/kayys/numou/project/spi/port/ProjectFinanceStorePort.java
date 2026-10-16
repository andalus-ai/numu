package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.project.domain.project.ProjectCostEntry;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.project.ProjectRevenueEntry;

import java.util.List;

/**
 * Append-only store for project cost and revenue entries.
 *
 * These entries are deliberately not part of the {@code Project}
 * aggregate: a real project can carry thousands of them, and loading
 * them with the project lifecycle would be disastrous. They arrive
 * from other bounded contexts (Accounting, Payables, Billing) and are
 * only aggregated for project profitability reporting.
 *
 * The contract stays synchronous on purpose - it is a simple append
 * and read projection, mirroring the budget capability's store port.
 */
public interface ProjectFinanceStorePort {

    List<ProjectCostEntry> findCosts(ProjectId projectId);

    void saveCost(ProjectCostEntry entry);

    List<ProjectRevenueEntry> findRevenues(ProjectId projectId);

    void saveRevenue(ProjectRevenueEntry entry);
}
