package tech.kayys.numou.survey.domain.service;

import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.logic.AnswerSheet;
import tech.kayys.numou.survey.domain.question.Question;
import tech.kayys.numou.survey.domain.quiz.QuestionScore;
import tech.kayys.numou.survey.domain.quiz.QuizSettings;
import tech.kayys.numou.survey.domain.quiz.ScoreResult;
import tech.kayys.numou.survey.domain.response.Response;
import tech.kayys.numou.survey.domain.survey.Survey;
import tech.kayys.numou.survey.domain.survey.SurveyKind;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Stateless domain service: skip-logic evaluation, submission validation, grading, time limits. */
public final class ResponseEvaluator {

    /** Tolerance for network latency when enforcing quiz time limits. */
    public static final Duration GRACE = Duration.ofSeconds(5);

    /**
     * Questions visible for the given answers. Evaluated in survey order; answers to hidden questions are
     * invisible to later conditions, so a hidden question can never "leak" into branching.
     */
    public List<Question> visibleQuestions(Survey survey, Map<QuestionId, AnswerValue> answers) {
        List<Question> visible = new ArrayList<>();
        Map<QuestionId, AnswerValue> effective = new HashMap<>();
        AnswerSheet sheet = id -> Optional.ofNullable(effective.get(id)).filter(a -> !AnswerValue.isEmpty(a));
        for (Question q : survey.questions()) {
            if (q.visibleIf().test(sheet)) {
                visible.add(q);
                AnswerValue a = answers.get(q.id());
                if (a != null) effective.put(q.id(), a);
            }
        }
        return visible;
    }

    /** Problems blocking submission, formatted "questionId:CODE". */
    public List<String> submissionProblems(Survey survey, Response response) {
        List<String> problems = new ArrayList<>();
        for (Question q : visibleQuestions(survey, response.answers())) {
            AnswerValue a = response.answers().get(q.id());
            boolean missing = a == null || AnswerValue.isEmpty(a);
            if (missing) {
                if (q.required()) problems.add(q.id() + ":REQUIRED");
            } else {
                q.type().validate(a).forEach(code -> problems.add(q.id() + ":" + code));
            }
        }
        return problems;
    }

    public Optional<ScoreResult> grade(Survey survey, Response response) {
        if (survey.kind() != SurveyKind.QUIZ) return Optional.empty();
        QuizSettings quiz = survey.quizSettings().orElseGet(QuizSettings::defaults);
        List<QuestionScore> items = new ArrayList<>();
        double earned = 0, max = 0;
        for (Question q : visibleQuestions(survey, response.answers())) {
            if (!q.scoring().isScored()) continue;
            double got = q.scoring().earned(response.answers().get(q.id()));
            items.add(new QuestionScore(q.id(), got, q.scoring().maxPoints(), got >= q.scoring().maxPoints()));
            earned += got;
            max += q.scoring().maxPoints();
        }
        double percent = max == 0 ? 0 : Math.round(earned / max * 10_000.0) / 100.0;
        return Optional.of(new ScoreResult(earned, max, percent, percent >= quiz.passingPercent(), items));
    }

    /** Strict deadline shown to the respondent; empty for untimed surveys. */
    public Optional<Instant> deadline(Survey survey, Response response) {
        return survey.quizSettings().filter(QuizSettings::timed).map(q -> response.startedAt().plus(q.timeLimit()));
    }

    public boolean isExpired(Survey survey, Response response, Instant now) {
        return deadline(survey, response).map(d -> now.isAfter(d.plus(GRACE))).orElse(false);
    }
}
