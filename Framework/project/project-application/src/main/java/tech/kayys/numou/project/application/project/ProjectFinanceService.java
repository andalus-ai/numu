package tech.kayys.syirkah.project.application.project;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.project.domain.project.InvalidProjectStateException;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectCostEntry;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectRevenueEntry;
import tech.kayys.syirkah.project.domain.project.ProjectStatus;
import tech.kayys.syirkah.project.spi.port.ProjectFinanceStorePort;
import tech.kayys.syirkah.project.spi.port.ProjectRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Financial view of a project: cost and revenue entries arrive from
 * Accounting, Payables and Billing, and are only aggregated here for
 * project profitability reporting.
 *
 * This service deliberately does not duplicate lifecycle logic - the
 * Project lifecycle is driven by the command handlers - it only guards
 * the rules that decide whether financial entries may still be booked.
 */
public final class ProjectFinanceService {

    private final ProjectRepository projects;

    private final ProjectFinanceStorePort entries;

    public ProjectFinanceService(
            ProjectRepository projects,
            ProjectFinanceStorePort entries
    ) {
        this.projects = Objects.requireNonNull(projects);
        this.entries = Objects.requireNonNull(entries);
    }

    /**
     * Books a cost against a project that is not finished yet.
     * Costs may already occur during planning, but never after the
     * project is completed or cancelled.
     */
    public Uni<ProjectCostEntry> recordCost(
            ProjectId projectId,
            String costCategory,
            BigDecimal amount,
            String currency,
            String sourceReference
    ) {
        return load(projectId).map(project -> {
            if (project.status() == ProjectStatus.COMPLETED
                    || project.status() == ProjectStatus.CANCELLED) {
                throw new InvalidProjectStateException(
                        "Project " + projectId.value()
                                + " cannot receive cost entries when "
                                + project.status()
                );
            }

            var entry = ProjectCostEntry.of(
                    projectId,
                    costCategory,
                    amount,
                    currency,
                    sourceReference
            );

            entries.saveCost(entry);
            return entry;
        });
    }

    /**
     * Recognises revenue for an ACTIVE project - revenue recognition
     * requires work in progress, not merely a registered project.
     */
    public Uni<ProjectRevenueEntry> recordRevenue(
            ProjectId projectId,
            String milestone,
            BigDecimal amount,
            String currency,
            String invoiceReference
    ) {
        return load(projectId).map(project -> {
            if (project.status() != ProjectStatus.ACTIVE) {
                throw new InvalidProjectStateException(
                        "Revenue can only be recognised for an ACTIVE project but was "
                                + project.status()
                );
            }

            var entry = ProjectRevenueEntry.of(
                    projectId,
                    milestone,
                    amount,
                    currency,
                    invoiceReference
            );

            entries.saveRevenue(entry);
            return entry;
        });
    }

    /** Aggregates the recorded entries into a profitability snapshot. */
    public Uni<ProjectProfitability> profitability(ProjectId projectId) {
        return load(projectId).map(project -> ProjectProfitability.compute(
                projectId,
                totalRevenues(entries.findRevenues(projectId)),
                totalCosts(entries.findCosts(projectId))
        ));
    }

    public Uni<List<ProjectCostEntry>> costs(ProjectId projectId) {
        return load(projectId).map(project -> entries.findCosts(projectId));
    }

    public Uni<List<ProjectRevenueEntry>> revenues(ProjectId projectId) {
        return load(projectId).map(project -> entries.findRevenues(projectId));
    }

    public Uni<Project> find(ProjectId projectId) {
        return load(projectId);
    }

    private Uni<Project> load(ProjectId projectId) {
        return Uni.createFrom()
                .completionStage(projects.findById(projectId))
                .map(maybeProject -> maybeProject.orElseThrow(() ->
                        ApplicationError.of(
                                "PROJECT_NOT_FOUND",
                                "Project does not exist: " + projectId.value()
                        ).toException()
                ));
    }

    private static BigDecimal totalCosts(List<ProjectCostEntry> entries) {
        return entries.stream()
                .map(ProjectCostEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalRevenues(List<ProjectRevenueEntry> entries) {
        return entries.stream()
                .map(ProjectRevenueEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
