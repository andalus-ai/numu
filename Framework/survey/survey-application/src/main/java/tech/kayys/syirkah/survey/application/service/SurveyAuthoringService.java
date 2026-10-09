package tech.kayys.syirkah.survey.application.service;

import tech.kayys.syirkah.survey.application.TenantContext;
import tech.kayys.syirkah.survey.application.port.in.SurveyAuthoringUseCase;
import tech.kayys.syirkah.survey.application.port.out.IdGenerator;
import tech.kayys.syirkah.survey.application.port.out.SurveyRepository;
import tech.kayys.syirkah.survey.application.port.out.TenantRepository;
import tech.kayys.syirkah.survey.domain.id.QuestionId;
import tech.kayys.syirkah.survey.domain.id.SectionId;
import tech.kayys.syirkah.survey.domain.id.SurveyId;
import tech.kayys.syirkah.survey.domain.question.Question;
import tech.kayys.syirkah.survey.domain.quiz.QuizSettings;
import tech.kayys.syirkah.survey.domain.survey.Survey;
import tech.kayys.syirkah.survey.domain.survey.SurveyKind;
import tech.kayys.syirkah.survey.domain.survey.SurveySettings;
import tech.kayys.syirkah.survey.domain.survey.SurveyStatus;
import tech.kayys.syirkah.survey.domain.tenant.Tenant;

import java.time.Clock;
import java.util.List;
import java.util.function.BiConsumer;

public final class SurveyAuthoringService implements SurveyAuthoringUseCase {
    private final SurveyRepository surveys;
    private final IdGenerator ids;
    private final Clock clock;
    private final TenantGate gate;

    public SurveyAuthoringService(SurveyRepository surveys, TenantRepository tenants, IdGenerator ids, Clock clock) {
        this.surveys = surveys;
        this.ids = ids;
        this.clock = clock;
        this.gate = new TenantGate(tenants, surveys);
    }

    @Override
    public Survey createSurvey(TenantContext ctx, CreateSurvey cmd) {
        Tenant tenant = gate.activeTenant(ctx.tenantId());
        SurveyKind kind = cmd.kind() == null ? SurveyKind.SURVEY : cmd.kind();
        if (kind == SurveyKind.QUIZ) tenant.ensureQuizAllowed();
        Survey s = Survey.draft(new SurveyId(ids.next()), ctx.tenantId(), cmd.title(), cmd.description(), kind, clock.instant());
        surveys.save(s);
        return s;
    }

    @Override
    public Survey updateDetails(TenantContext ctx, SurveyId id, String title, String description) {
        return edit(ctx, id, (s, tenant) -> s.updateDetails(title, description, clock.instant()));
    }

    @Override
    public Survey addSection(TenantContext ctx, AddSection cmd) {
        return edit(ctx, cmd.survey(),
                (s, tenant) -> s.addSection(new SectionId(ids.next()), cmd.title(), cmd.description(), clock.instant()));
    }

    @Override
    public Survey addQuestion(TenantContext ctx, AddQuestion cmd) {
        return edit(ctx, cmd.survey(), (s, tenant) -> {
            tenant.ensureQuestionQuota(s.questionCount());
            s.addQuestion(cmd.section(), cmd.question(), clock.instant());
        });
    }

    @Override
    public Survey updateQuestion(TenantContext ctx, SurveyId id, Question question) {
        return edit(ctx, id, (s, tenant) -> s.replaceQuestion(question, clock.instant()));
    }

    @Override
    public Survey removeQuestion(TenantContext ctx, SurveyId id, QuestionId question) {
        return edit(ctx, id, (s, tenant) -> s.removeQuestion(question, clock.instant()));
    }

    @Override
    public Survey moveQuestion(TenantContext ctx, MoveQuestion cmd) {
        return edit(ctx, cmd.survey(),
                (s, tenant) -> s.moveQuestion(cmd.question(), cmd.toSection(), cmd.index(), clock.instant()));
    }

    @Override
    public Survey updateSettings(TenantContext ctx, SurveyId id, SurveySettings settings) {
        return edit(ctx, id, (s, tenant) -> s.updateSettings(settings, clock.instant()));
    }

    @Override
    public Survey configureQuiz(TenantContext ctx, SurveyId id, QuizSettings quiz) {
        return edit(ctx, id, (s, tenant) -> s.configureQuiz(quiz, clock.instant()));
    }

    @Override
    public Survey getSurvey(TenantContext ctx, SurveyId id) {
        gate.activeTenant(ctx.tenantId());
        return gate.survey(ctx.tenantId(), id);
    }

    @Override
    public List<Survey> listSurveys(TenantContext ctx, SurveyStatus statusOrNull) {
        gate.activeTenant(ctx.tenantId());
        return surveys.findByTenant(ctx.tenantId(), statusOrNull);
    }

    /** load -> mutate (domain rules live in the aggregate) -> save. */
    private Survey edit(TenantContext ctx, SurveyId id, BiConsumer<Survey, Tenant> change) {
        Tenant tenant = gate.activeTenant(ctx.tenantId());
        Survey s = gate.survey(ctx.tenantId(), id);
        change.accept(s, tenant);
        surveys.save(s);
        return s;
    }
}
