package tech.kayys.numou.construction.domain.consultancy;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import java.util.Objects;
import java.util.UUID;

/**
 * A consultant's professional-services engagement. Project, client and
 * consultant references are opaque IDs so this model can be composed with
 * CRM, identity, project and finance capabilities without owning them.
 *
 * Signed commercial terms and post-activation scope changes belong to the
 * contract/change-control workflow; this aggregate does not silently mutate
 * an active engagement's agreed scope.
 */
public final class ConsultancyEngagement extends AbstractAggregateRoot<ConsultancyEngagementId> {
    private final UUID projectId;
    private final UUID clientOrganizationId;
    private final UUID consultantOrganizationId;
    private final String engagementNumber;
    private String title;
    private String scopeStatement;
    private int scopeRevision;
    private ConsultancyEngagementStatus status;
    private String cancellationReason;

    private ConsultancyEngagement(
            ConsultancyEngagementId id,
            UUID projectId,
            UUID clientOrganizationId,
            UUID consultantOrganizationId,
            String engagementNumber,
            String title,
            String scopeStatement) {
        super(Objects.requireNonNull(id, "Engagement id cannot be null"));
        this.projectId = Objects.requireNonNull(projectId, "Project id cannot be null");
        this.clientOrganizationId = Objects.requireNonNull(clientOrganizationId, "Client organization cannot be null");
        this.consultantOrganizationId = Objects.requireNonNull(consultantOrganizationId, "Consultant organization cannot be null");
        if (clientOrganizationId.equals(consultantOrganizationId)) {
            throw new IllegalArgumentException("Client and consultant organizations must be different");
        }
        this.engagementNumber = requireText(engagementNumber, "Engagement number");
        this.title = requireText(title, "Engagement title");
        this.scopeStatement = requireText(scopeStatement, "Scope statement");
        this.scopeRevision = 1;
        this.status = ConsultancyEngagementStatus.DRAFT;
    }

    public static ConsultancyEngagement create(
            UUID projectId,
            UUID clientOrganizationId,
            UUID consultantOrganizationId,
            String engagementNumber,
            String title,
            String scopeStatement) {
        return new ConsultancyEngagement(
                ConsultancyEngagementId.generate(),
                projectId,
                clientOrganizationId,
                consultantOrganizationId,
                engagementNumber,
                title,
                scopeStatement);
    }

    /** Revises a proposal before activation; active scope changes need formal change control. */
    public void reviseProposal(String title, String scopeStatement) {
        if (status != ConsultancyEngagementStatus.DRAFT && status != ConsultancyEngagementStatus.PROPOSED) {
            throw new IllegalStateException("Proposal can only be revised before activation");
        }
        this.title = requireText(title, "Engagement title");
        this.scopeStatement = requireText(scopeStatement, "Scope statement");
        this.scopeRevision = Math.incrementExact(scopeRevision);
        this.status = ConsultancyEngagementStatus.DRAFT;
    }

    public void submitProposal() {
        if (status != ConsultancyEngagementStatus.DRAFT) {
            throw new IllegalStateException("Only a draft engagement can be submitted as a proposal");
        }
        status = ConsultancyEngagementStatus.PROPOSED;
    }

    public void activate() {
        if (status != ConsultancyEngagementStatus.PROPOSED) {
            throw new IllegalStateException("Only a submitted proposal can be activated");
        }
        status = ConsultancyEngagementStatus.ACTIVE;
    }

    public void complete() {
        if (status != ConsultancyEngagementStatus.ACTIVE) {
            throw new IllegalStateException("Only an active engagement can be completed");
        }
        status = ConsultancyEngagementStatus.COMPLETED;
    }

    public void cancel(String reason) {
        if (status == ConsultancyEngagementStatus.COMPLETED || status == ConsultancyEngagementStatus.CANCELLED) {
            throw new IllegalStateException("Completed or cancelled engagement cannot be cancelled");
        }
        this.cancellationReason = requireText(reason, "Cancellation reason");
        status = ConsultancyEngagementStatus.CANCELLED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    public UUID projectId() { return projectId; }
    public UUID clientOrganizationId() { return clientOrganizationId; }
    public UUID consultantOrganizationId() { return consultantOrganizationId; }
    public String engagementNumber() { return engagementNumber; }
    public String title() { return title; }
    public String scopeStatement() { return scopeStatement; }
    public int scopeRevision() { return scopeRevision; }
    public ConsultancyEngagementStatus status() { return status; }
    public String cancellationReason() { return cancellationReason; }
}
