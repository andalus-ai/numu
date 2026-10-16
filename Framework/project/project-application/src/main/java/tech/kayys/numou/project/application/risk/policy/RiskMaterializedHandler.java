package tech.kayys.syirkah.project.application.risk.policy;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.project.domain.risk.Issue;
import tech.kayys.syirkah.project.domain.risk.IssueId;
import tech.kayys.syirkah.project.domain.risk.event.RiskMaterialized;
import tech.kayys.syirkah.project.spi.port.IssueRepository;

import java.util.Objects;

/**
 * Application-side consumer of {@code RiskMaterialized}: turns the
 * event into an Issue through the policy — never through the Risk
 * aggregate.
 *
 * Idempotent by design: if an issue already exists for this risk the
 * event is ignored, so a replayed event cannot raise a duplicate.
 */
public final class RiskMaterializedHandler {

    private final IssueRepository issues;
    private final RiskMaterializationPolicy policy;
    private final EventPublisher eventPublisher;

    public RiskMaterializedHandler(
            IssueRepository issues,
            RiskMaterializationPolicy policy,
            EventPublisher eventPublisher
    ) {
        this.issues = Objects.requireNonNull(issues);
        this.policy = Objects.requireNonNull(policy);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    public Uni<IssueId> handle(RiskMaterialized event) {
        return Uni.createFrom()
                .completionStage(issues.findByRiskId(event.riskId()))
                .onItem()
                .transformToUni(existing -> {

                    if (!existing.isEmpty()) {
                        return Uni.createFrom().item(existing.get(0).id());
                    }

                    IssueDraft draft = policy.createIssue(event);

                    var issue = Issue.fromMaterializedRisk(
                            IssueId.generate(),
                            draft.projectId(),
                            event.riskId(),
                            draft.number(),
                            draft.title(),
                            draft.description(),
                            draft.severity(),
                            draft.priority(),
                            draft.ownerId()
                    );

                    return Uni.createFrom()
                            .completionStage(issues.save(issue))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(saved.id()));
                });
    }
}
