package tech.kayys.numou.survey.demo;

import tech.kayys.numou.survey.adapter.memory.*;
import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.application.port.in.*;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase.*;
import tech.kayys.numou.survey.application.port.in.SurveyAuthoringUseCase.*;
import tech.kayys.numou.survey.application.service.*;
import tech.kayys.numou.survey.domain.DomainException;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.*;
import tech.kayys.numou.survey.domain.logic.CompareOp;
import tech.kayys.numou.survey.domain.logic.Condition;
import tech.kayys.numou.survey.domain.question.Option;
import tech.kayys.numou.survey.domain.question.Question;
import tech.kayys.numou.survey.domain.question.QuestionType.*;
import tech.kayys.numou.survey.domain.quiz.*;
import tech.kayys.numou.survey.domain.survey.*;
import tech.kayys.numou.survey.domain.tenant.Plan;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

/**
 * End-to-end walkthrough of the core using only in-memory adapters (no framework).
 * Doubles as a smoke test: it throws (non-zero exit) if any expectation is violated.
 */
public final class DemoApp {

    public static void main(String[] args) {
        // ---- composition root (what the Quarkus bootstrap module does with CDI producers)
        var surveys = new InMemorySurveyRepository();
        var responses = new InMemoryResponseRepository();
        var tenants = new InMemoryTenantRepository();
        var events = new CollectingEventPublisher();
        var ids = new UuidIdGenerator();
        Clock clock = Clock.systemUTC();

        TenantAdminUseCase admin = new TenantAdminService(tenants);
        SurveyAuthoringUseCase authoring = new SurveyAuthoringService(surveys, tenants, ids, clock);
        SurveyLifecycleUseCase lifecycle = new SurveyLifecycleService(surveys, tenants, events, ids, clock);
        ResponseUseCase respond = new ResponseService(surveys, responses, tenants, events, ids, clock);
        ResultsUseCase results = new ResultsService(surveys, responses, tenants);

        TenantId acmeTenantId = TenantId.of(UUID.nameUUIDFromBytes("acme".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        TenantId globexTenantId = TenantId.of(UUID.nameUUIDFromBytes("globex".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        admin.register(new TenantAdminUseCase.RegisterTenant(acmeTenantId, "Acme Corp", Plan.PRO));
        admin.register(new TenantAdminUseCase.RegisterTenant(globexTenantId, "Globex", Plan.FREE));
        var acme = new TenantContext(acmeTenantId, "alice");
        var globex = new TenantContext(globexTenantId, "bob");

        // =============================================================== 1) a feedback SURVEY
        Survey s = authoring.createSurvey(acme, new CreateSurvey("Customer feedback", "Q3 pulse", SurveyKind.SURVEY));
        s = authoring.addSection(acme, new AddSection(s.id(), "About you", ""));
        SectionId about = s.sections().get(0).id();
        s = authoring.addSection(acme, new AddSection(s.id(), "Your experience", ""));
        SectionId exp = s.sections().get(1).id();
        SurveyId sid = s.id();

        add(authoring, acme, sid, about, Question.of("name", "Your name", new ShortText(60, null)));
        add(authoring, acme, sid, about, Question.of("source", "How did you hear about us?", new SingleChoice(
                List.of(Option.of("ad", "Advert"), Option.of("friend", "Friend"), Option.of("web", "Web search")), true)).mandatory());
        add(authoring, acme, sid, exp, Question.of("nps", "How likely are you to recommend us?", new Scale(0, 10, "Never", "Definitely"))
                .mandatory());
        add(authoring, acme, sid, exp, Question.of("why_low", "Sorry to hear that - what went wrong?", new LongText(2000))
                .mandatory().showIf(new Condition.Compare(new QuestionId("nps"), CompareOp.LTE, 6)));
        add(authoring, acme, sid, exp, Question.of("features", "Which features do you use?", new MultiChoice(
                List.of(Option.of("a", "Reports"), Option.of("b", "Alerts"), Option.of("c", "API")), 1, 2, false)));
        add(authoring, acme, sid, exp, Question.of("grid", "Rate each aspect", new Matrix(
                List.of(Option.of("speed", "Speed"), Option.of("ux", "Usability")),
                List.of(Option.of("bad", "Bad"), Option.of("ok", "OK"), Option.of("good", "Good")), false, true)));
        add(authoring, acme, sid, exp, Question.of("prio", "Rank what we should build next", new Ranking(
                List.of(Option.of("mobile", "Mobile app"), Option.of("sso", "SSO"), Option.of("export", "Exports")), null)));
        add(authoring, acme, sid, exp, Question.of("stars", "Overall rating", new Rating(5)));
        add(authoring, acme, sid, exp, Question.of("budget", "Monthly budget (USD)", new NumberInput(0.0, 100000.0, false)));

        System.out.println("Not-ready problems (expected none): " + lifecycle.checkReadiness(acme, sid));
        lifecycle.publish(acme, sid);

        // promoter: skips the "why_low" follow-up
        var r1 = respond.start(acme, new StartResponse(sid, "u1"));
        System.out.println("Visible questions at start: " + r1.visibleQuestions().size() + " (why_low hidden)");
        save(respond, acme, r1.id(), "name", new AnswerValue.Text("Ann"));
        save(respond, acme, r1.id(), "source", AnswerValue.Choice.of("friend"));
        save(respond, acme, r1.id(), "nps", new AnswerValue.Numeric(10));
        save(respond, acme, r1.id(), "features", AnswerValue.Choice.of("a", "c"));
        save(respond, acme, r1.id(), "grid", AnswerValue.Matrix.of(Map.of("speed", List.of("good"), "ux", List.of("ok"))));
        save(respond, acme, r1.id(), "prio", AnswerValue.Ranking.of("sso", "mobile", "export"));
        save(respond, acme, r1.id(), "stars", new AnswerValue.Numeric(5));
        respond.submit(acme, r1.id());

        // detractor: the follow-up becomes visible AND required
        var r2 = respond.start(acme, new StartResponse(sid, "u2"));
        save(respond, acme, r2.id(), "source", AnswerValue.Choice.other("podcast"));
        var after = save(respond, acme, r2.id(), "nps", new AnswerValue.Numeric(3));
        check(after.visibleQuestions().stream().anyMatch(q -> q.id().value().equals("why_low")), "why_low should appear for nps=3");
        expectFailure("submit without required follow-up", () -> respond.submit(acme, r2.id()));
        save(respond, acme, r2.id(), "why_low", new AnswerValue.Text("Too slow"));
        save(respond, acme, r2.id(), "grid", AnswerValue.Matrix.of(Map.of("speed", List.of("bad"), "ux", List.of("bad"))));
        respond.submit(acme, r2.id());

        expectFailure("same respondent twice", () -> respond.start(acme, new StartResponse(sid, "u1")));
        var summary = results.summary(acme, sid);
        System.out.println("Survey responses: " + summary.totalSubmitted());
        summary.questions().stream().filter(q -> q.kind().equals("scale") || q.kind().equals("single_choice"))
                .forEach(q -> System.out.println("  " + q.question() + " -> " + q.stats()));

        // editing a published survey is blocked; revise creates v2
        expectFailure("edit published survey", () -> authoring.updateDetails(acme, sid, "x", "y"));
        Survey v2 = lifecycle.revise(acme, sid);
        System.out.println("Revised: v" + v2.version() + " (previous " + v2.previousVersion().orElseThrow() + ")");

        // =============================================================== 2) a QUIZ
        Survey q = authoring.createSurvey(acme, new CreateSurvey("Java basics", "Quick check", SurveyKind.QUIZ));
        q = authoring.addSection(acme, new AddSection(q.id(), "Questions", ""));
        SectionId qs = q.sections().get(0).id();
        SurveyId qid = q.id();
        authoring.configureQuiz(acme, qid, new QuizSettings(60, Duration.ofMinutes(10), 2, true, ResultsVisibility.IMMEDIATE, true));

        add(authoring, acme, qid, qs, Question.of("q1", "Which keyword creates an immutable record?", new SingleChoice(
                List.of(Option.of("a", "class"), Option.of("b", "record"), Option.of("c", "struct")), false))
                .mandatory().scored(new Scoring.ExactChoice(Set.of(new OptionId("b")), 2)).explained("Since Java 16."));
        add(authoring, acme, qid, qs, Question.of("q2", "Select the checked exceptions", new MultiChoice(
                List.of(Option.of("a", "IOException"), Option.of("b", "NullPointerException"), Option.of("c", "SQLException")),
                1, null, false)).scored(new Scoring.PartialChoices(Set.of(new OptionId("a"), new OptionId("c")), 4, true)));
        add(authoring, acme, qid, qs, Question.of("q3", "Java collections framework size of List.of(1,2,3)?", new NumberInput(null, null, true))
                .scored(new Scoring.NumericMatch(3, 0, 1)));
        add(authoring, acme, qid, qs, Question.of("q4", "Name the keyword that prevents subclassing", new ShortText(20, null))
                .scored(new Scoring.TextMatch(Set.of("final", "sealed"), true, 1)));
        add(authoring, acme, qid, qs, Question.of("q5", "Is String immutable?", new YesNo()).scored(new Scoring.BoolMatch(true, 1)));
        add(authoring, acme, qid, qs, Question.of("q6", "Order the phases: compile, link, run", new Ranking(
                List.of(Option.of("compile", "Compile"), Option.of("link", "Link"), Option.of("run", "Run")), null))
                .scored(new Scoring.OrderMatch(List.of(new OptionId("compile"), new OptionId("link"), new OptionId("run")), 1)));

        lifecycle.publish(acme, qid);
        var attempt = respond.start(acme, new StartResponse(qid, "student-1"));
        save(respond, acme, attempt.id(), "q1", AnswerValue.Choice.of("b"));
        save(respond, acme, attempt.id(), "q2", AnswerValue.Choice.of("a", "c"));
        save(respond, acme, attempt.id(), "q3", new AnswerValue.Numeric(3));
        save(respond, acme, attempt.id(), "q4", new AnswerValue.Text("FINAL"));
        save(respond, acme, attempt.id(), "q5", new AnswerValue.Bool(false));       // wrong
        save(respond, acme, attempt.id(), "q6", AnswerValue.Ranking.of("compile", "run", "link")); // wrong
        var done = respond.submit(acme, attempt.id());
        ScoreResult score = done.score().orElseThrow();
        System.out.printf("Quiz score: %.0f/%.0f = %.2f%% passed=%s%n", score.earned(), score.max(), score.percent(), score.passed());
        check(score.earned() == 8 && score.max() == 10 && score.passed(), "expected 8/10 and pass");
        var quizSummary = results.summary(acme, qid);
        System.out.println("Quiz stats: " + quizSummary.quiz().orElseThrow());

        // a bad quiz definition is caught before publishing
        Survey bad = authoring.createSurvey(acme, new CreateSurvey("Broken quiz", "", SurveyKind.QUIZ));
        bad = authoring.addSection(acme, new AddSection(bad.id(), "S", ""));
        authoring.addQuestion(acme, new AddQuestion(bad.id(), bad.sections().get(0).id(), Question.of("x", "Pick", new SingleChoice(
                List.of(Option.of("a", "A")), false)).scored(new Scoring.ExactChoice(Set.of(new OptionId("zzz")), 1))));
        System.out.println("Readiness problems for broken quiz: " + lifecycle.checkReadiness(acme, bad.id()));

        // =============================================================== 3) multitenancy
        expectFailure("globex reading acme's survey", () -> authoring.getSurvey(globex, sid));
        expectFailure("globex (FREE plan) creating a quiz", () -> authoring.createSurvey(globex, new CreateSurvey("Q", "", SurveyKind.QUIZ)));
        check(authoring.listSurveys(globex, null).isEmpty(), "globex must see none of acme's surveys");
        admin.deactivate(globexTenantId);
        expectFailure("deactivated tenant", () -> authoring.listSurveys(globex, null));

        System.out.println("Events published: " + events.published().size());
        System.out.println("\nAll checks passed.");
    }

    // ---------------------------------------------------------------- tiny helpers
    private static void add(SurveyAuthoringUseCase a, TenantContext c, SurveyId s, SectionId sec, Question q) {
        a.addQuestion(c, new AddQuestion(s, sec, q));
    }

    private static ResponseSession save(ResponseUseCase r, TenantContext c, ResponseId id, String q, AnswerValue v) {
        return r.saveAnswer(c, new SaveAnswer(id, new QuestionId(q), v));
    }

    private static void expectFailure(String what, Runnable action) {
        try {
            action.run();
        } catch (DomainException e) {
            System.out.println("  [blocked] " + what + " -> " + e.code() + ": " + e.getMessage()
                    + (e.details().isEmpty() ? "" : " " + e.details()));
            return;
        }
        throw new AssertionError("Expected failure but succeeded: " + what);
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}
