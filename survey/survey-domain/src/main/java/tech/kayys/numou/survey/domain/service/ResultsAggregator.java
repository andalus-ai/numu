package tech.kayys.numou.survey.domain.service;

import tech.kayys.numou.survey.domain.analytics.QuestionResult;
import tech.kayys.numou.survey.domain.analytics.QuizStats;
import tech.kayys.numou.survey.domain.analytics.SurveyResults;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.OptionId;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.question.Question;
import tech.kayys.numou.survey.domain.question.QuestionType;
import tech.kayys.numou.survey.domain.quiz.QuestionScore;
import tech.kayys.numou.survey.domain.quiz.ScoreResult;
import tech.kayys.numou.survey.domain.response.Response;
import tech.kayys.numou.survey.domain.survey.Survey;

import java.time.LocalDate;
import java.util.*;

/**
 * In-memory aggregation over submitted responses. Fine for small/medium volumes; for large datasets
 * implement a dedicated read-model port (SQL GROUP BY / OLAP) and keep this as the reference semantics.
 */
public final class ResultsAggregator {

    public SurveyResults aggregate(Survey survey, List<Response> submitted) {
        List<QuestionResult> results = new ArrayList<>();
        for (Question q : survey.questions()) {
            List<AnswerValue> answers = submitted.stream()
                    .map(r -> r.answers().get(q.id()))
                    .filter(Objects::nonNull)
                    .filter(a -> !AnswerValue.isEmpty(a))
                    .toList();
            results.add(new QuestionResult(q.id(), q.title(), q.type().kind(), answers.size(), stats(q.type(), answers)));
        }
        return new SurveyResults(survey.id(), submitted.size(), results, quizStats(survey, submitted));
    }

    private Optional<QuizStats> quizStats(Survey survey, List<Response> submitted) {
        List<ScoreResult> scores = submitted.stream().flatMap(r -> r.score().stream()).toList();
        if (scores.isEmpty()) return Optional.empty();
        DoubleSummaryStatistics pct = scores.stream().mapToDouble(ScoreResult::percent).summaryStatistics();
        double passRate = round(100.0 * scores.stream().filter(ScoreResult::passed).count() / scores.size());
        Map<QuestionId, double[]> perQ = new LinkedHashMap<>(); // [correct, total]
        for (ScoreResult s : scores) {
            for (QuestionScore qs : s.items()) {
                double[] c = perQ.computeIfAbsent(qs.question(), k -> new double[2]);
                if (qs.fullyCorrect()) c[0]++;
                c[1]++;
            }
        }
        Map<QuestionId, Double> rate = new LinkedHashMap<>();
        perQ.forEach((k, v) -> rate.put(k, round(100.0 * v[0] / v[1])));
        return Optional.of(new QuizStats(scores.size(), round(pct.getAverage()), passRate, pct.getMax(), pct.getMin(), rate));
    }

    private Map<String, Object> stats(QuestionType type, List<AnswerValue> a) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (type) {
            case QuestionType.ShortText x -> text(m, a);
            case QuestionType.LongText x -> text(m, a);
            case QuestionType.NumberInput x -> numeric(m, a, false);
            case QuestionType.Rating x -> numeric(m, a, true);
            case QuestionType.Scale x -> {
                numeric(m, a, true);
                if (x.min() == 0 && x.max() == 10) nps(m, a);
            }
            case QuestionType.DateInput x -> dates(m, a);
            case QuestionType.YesNo x -> yesNo(m, a);
            case QuestionType.SingleChoice x -> choice(m, a);
            case QuestionType.MultiChoice x -> choice(m, a);
            case QuestionType.Ranking x -> ranking(m, a);
            case QuestionType.Matrix x -> matrix(m, a);
        }
        return m;
    }

    private void text(Map<String, Object> m, List<AnswerValue> a) {
        double avg = a.stream().filter(v -> v instanceof AnswerValue.Text).mapToInt(v -> ((AnswerValue.Text) v).value().length())
                .average().orElse(0);
        m.put("averageLength", round(avg));
    }

    private List<Double> numbers(List<AnswerValue> a) {
        return a.stream().filter(v -> v instanceof AnswerValue.Numeric).map(v -> ((AnswerValue.Numeric) v).value()).toList();
    }

    private void numeric(Map<String, Object> m, List<AnswerValue> a, boolean distribution) {
        List<Double> nums = numbers(a);
        DoubleSummaryStatistics s = nums.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        if (nums.isEmpty()) return;
        m.put("min", s.getMin());
        m.put("max", s.getMax());
        m.put("average", round(s.getAverage()));
        if (distribution) {
            Map<Long, Long> d = new TreeMap<>();
            nums.forEach(n -> d.merge(Math.round(n), 1L, Long::sum));
            m.put("distribution", d);
        }
    }

    private void nps(Map<String, Object> m, List<AnswerValue> a) {
        List<Double> nums = numbers(a);
        if (nums.isEmpty()) return;
        long promoters = nums.stream().filter(n -> n >= 9).count();
        long detractors = nums.stream().filter(n -> n <= 6).count();
        m.put("nps", round(100.0 * (promoters - detractors) / nums.size()));
    }

    private void dates(Map<String, Object> m, List<AnswerValue> a) {
        List<LocalDate> ds = a.stream().filter(v -> v instanceof AnswerValue.Day).map(v -> ((AnswerValue.Day) v).value()).toList();
        if (ds.isEmpty()) return;
        m.put("earliest", Collections.min(ds).toString());
        m.put("latest", Collections.max(ds).toString());
    }

    private void yesNo(Map<String, Object> m, List<AnswerValue> a) {
        long yes = a.stream().filter(v -> v instanceof AnswerValue.Bool b && b.value()).count();
        m.put("yes", yes);
        m.put("no", a.size() - yes);
    }

    private void choice(Map<String, Object> m, List<AnswerValue> a) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long other = 0;
        for (AnswerValue v : a) {
            if (v instanceof AnswerValue.Choice c) {
                c.selected().forEach(o -> counts.merge(o.value(), 1L, Long::sum));
                if (!c.otherText().isBlank()) other++;
            }
        }
        m.put("counts", counts);
        m.put("other", other);
    }

    private void ranking(Map<String, Object> m, List<AnswerValue> a) {
        Map<String, double[]> pos = new LinkedHashMap<>(); // [sum, n]
        for (AnswerValue v : a) {
            if (v instanceof AnswerValue.Ranking r) {
                for (int i = 0; i < r.order().size(); i++) {
                    double[] p = pos.computeIfAbsent(r.order().get(i).value(), k -> new double[2]);
                    p[0] += i + 1;
                    p[1]++;
                }
            }
        }
        Map<String, Double> avg = new LinkedHashMap<>();
        pos.forEach((k, v) -> avg.put(k, round(v[0] / v[1])));
        m.put("averagePosition", avg);
    }

    private void matrix(Map<String, Object> m, List<AnswerValue> a) {
        Map<String, Long> cells = new LinkedHashMap<>();
        for (AnswerValue v : a) {
            if (v instanceof AnswerValue.Matrix mx) {
                mx.cells().forEach((row, cols) -> {
                    for (OptionId c : cols) cells.merge(row.value() + "|" + c.value(), 1L, Long::sum);
                });
            }
        }
        m.put("cells", cells);
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
