package tech.kayys.syirkah.project.domain.risk;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.event.RiskAssessed;
import tech.kayys.syirkah.project.domain.risk.event.RiskClosed;
import tech.kayys.syirkah.project.domain.risk.event.RiskIdentified;
import tech.kayys.syirkah.project.domain.risk.event.RiskMaterialized;
import tech.kayys.syirkah.project.domain.risk.event.RiskMonitoringStarted;
import tech.kayys.syirkah.project.domain.risk.event.RiskResponsePlanned;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Risk aggregate (P10.10) — owns the risk lifecycle and its current
 * assessment.
 *
 * The score is always derived ({@link RiskScore#of}), never stored as
 * a hand-entered number, so a register row can always be trusted. A
 * closed risk rejects every further transition: closure is terminal,
 * not a soft delete.
 */
public final class Risk
        extends AbstractAggregateRoot<RiskId> {

    private final ProjectId projectId;

    private final String number;
    private final String title;
    private final String description;

    private final RiskCategory category;
    private final RiskSource source;

    private RiskStatus status;

    private Probability probability;
    private ImpactLevel impact;
    private RiskScore score;

    private RiskResponse response;

    private final LocalDate identifiedDate;
    private LocalDate targetDate;

    private Risk(
            RiskId id,
            ProjectId projectId,
            String number,
            String title,
            String description,
            RiskCategory category,
            RiskSource source,
            LocalDate identifiedDate,
            LocalDate targetDate
    ) {
        super(id);

        this.projectId = Objects.requireNonNull(
                projectId,
                "projectId cannot be null"
        );

        this.number = requireText(number, "number");
        this.title = requireText(title, "title");
        this.description = requireText(description, "description");

        this.category = Objects.requireNonNull(
                category,
                "category cannot be null"
        );

        this.source = Objects.requireNonNull(
                source,
                "source cannot be null"
        );

        this.identifiedDate = Objects.requireNonNull(
                identifiedDate,
                "identifiedDate cannot be null"
        );

        this.targetDate = Objects.requireNonNull(
                targetDate,
                "targetDate cannot be null"
        );

        if (targetDate.isBefore(identifiedDate)) {
            throw new IllegalArgumentException(
                    "Target date cannot be before identified date"
            );
        }

        this.status = RiskStatus.IDENTIFIED;

        raise(new RiskIdentified(
                UUID.randomUUID(),
                Instant.now(),
                id,
                projectId,
                number,
                category,
                source
        ));
    }

    public static Risk identify(
            RiskId id,
            ProjectId projectId,
            String number,
            String title,
            String description,
            RiskCategory category,
            RiskSource source,
            LocalDate identifiedDate,
            LocalDate targetDate
    ) {
        return new Risk(
                id,
                projectId,
                number,
                title,
                description,
                category,
                source,
                identifiedDate,
                targetDate
        );
    }

    public void assess(Probability probability, ImpactLevel impact) {
        requireNotClosed();

        this.probability = Objects.requireNonNull(
                probability,
                "probability cannot be null"
        );

        this.impact = Objects.requireNonNull(
                impact,
                "impact cannot be null"
        );

        this.score = RiskScore.of(probability, impact);
        this.status = RiskStatus.ASSESSED;

        raise(new RiskAssessed(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                projectId,
                score
        ));
    }

    public void planResponse(RiskResponse response) {
        requireStatus(
                RiskStatus.ASSESSED,
                RiskStatus.RESPONSE_PLANNED,
                RiskStatus.MONITORED
        );

        this.response = Objects.requireNonNull(
                response,
                "response cannot be null"
        );

        this.status = RiskStatus.RESPONSE_PLANNED;

        raise(new RiskResponsePlanned(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                projectId,
                response,
                score
        ));
    }

    public void monitor() {
        requireStatus(
                RiskStatus.RESPONSE_PLANNED,
                RiskStatus.MONITORED
        );

        this.status = RiskStatus.MONITORED;

        raise(new RiskMonitoringStarted(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                projectId
        ));
    }

    public void materialize() {
        requireStatus(
                RiskStatus.MONITORED,
                RiskStatus.RESPONSE_PLANNED,
                RiskStatus.ASSESSED
        );

        this.status = RiskStatus.MATERIALIZED;

        raise(new RiskMaterialized(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                projectId,
                number,
                title,
                description,
                score,
                response
        ));
    }

    public void close() {
        if (status == RiskStatus.CLOSED) {
            throw new InvalidRiskStateException("Risk is already closed");
        }

        this.status = RiskStatus.CLOSED;

        raise(new RiskClosed(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                projectId
        ));
    }

    private void requireNotClosed() {
        if (status == RiskStatus.CLOSED) {
            throw new InvalidRiskStateException("Closed risk cannot be modified");
        }
    }

    private void requireStatus(RiskStatus... allowed) {
        for (var candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }

        throw new InvalidRiskStateException(
                "Invalid risk state: " + status
        );
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }

        return value.trim();
    }

    public ProjectId projectId() {
        return projectId;
    }

    public String number() {
        return number;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public RiskCategory category() {
        return category;
    }

    public RiskSource source() {
        return source;
    }

    public RiskStatus status() {
        return status;
    }

    public Probability probability() {
        return probability;
    }

    public ImpactLevel impact() {
        return impact;
    }

    public RiskScore score() {
        return score;
    }

    public RiskResponse response() {
        return response;
    }

    public LocalDate identifiedDate() {
        return identifiedDate;
    }

    public LocalDate targetDate() {
        return targetDate;
    }
}