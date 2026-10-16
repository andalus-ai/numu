package tech.kayys.numou.survey.domain.tenant;

import tech.kayys.numou.survey.domain.DomainException;
import tech.kayys.numou.survey.domain.ErrorCode;
import tech.kayys.numou.survey.domain.Guard;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/** The isolation boundary: every survey/response belongs to exactly one tenant, and the plan sets its quotas. */
public record Tenant(TenantId id, String name, Plan plan, boolean active) {
    public Tenant {
        Guard.notNull(id, "tenant id");
        name = Guard.notBlank(name, "tenant name");
        Guard.notNull(plan, "plan");
    }

    public static Tenant register(TenantId id, String name, Plan plan) { return new Tenant(id, name, plan, true); }

    public Tenant withPlan(Plan p) { return new Tenant(id, name, p, active); }
    public Tenant deactivate() { return new Tenant(id, name, plan, false); }

    public void ensureActive() {
        if (!active) throw new DomainException(ErrorCode.FORBIDDEN, "Tenant is deactivated: " + id);
    }

    public void ensureQuizAllowed() {
        if (!plan.quizEnabled()) throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "Plan '" + plan.name() + "' does not include quizzes");
    }

    public void ensureQuestionQuota(int existingQuestions) {
        if (existingQuestions + 1 > plan.maxQuestionsPerSurvey())
            throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "Plan '" + plan.name() + "' allows at most "
                    + plan.maxQuestionsPerSurvey() + " questions per survey");
    }

    public void ensurePublishQuota(long publishedNow) {
        if (publishedNow + 1 > plan.maxPublishedSurveys())
            throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "Plan '" + plan.name() + "' allows at most "
                    + plan.maxPublishedSurveys() + " published surveys");
    }

    public void ensureResponseQuota(long submittedSoFar) {
        if (submittedSoFar >= plan.maxResponsesPerSurvey())
            throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "Plan '" + plan.name() + "' allows at most "
                    + plan.maxResponsesPerSurvey() + " responses per survey");
    }
}
