package tech.kayys.numou.survey.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import tech.kayys.numou.survey.domain.DomainException;
import tech.kayys.numou.survey.domain.ErrorCode;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.OptionId;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.logic.CompareOp;
import tech.kayys.numou.survey.domain.logic.Condition;
import tech.kayys.numou.survey.domain.question.Option;
import tech.kayys.numou.survey.domain.question.Question;
import tech.kayys.numou.survey.domain.question.QuestionType;
import tech.kayys.numou.survey.domain.quiz.QuizSettings;
import tech.kayys.numou.survey.domain.quiz.ResultsVisibility;
import tech.kayys.numou.survey.domain.quiz.Scoring;
import tech.kayys.numou.survey.domain.survey.Section;
import tech.kayys.numou.survey.domain.survey.Survey;
import tech.kayys.numou.survey.domain.survey.SurveySettings;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Translates the JSON wire format to/from domain objects. Kept by hand (no annotations on the domain)
 * so the core stays framework-free. Wire format is documented in the README.
 */
public final class SurveyJson {
    private SurveyJson() {}

    // =============================================================== parsing

    public static Question parseQuestion(JsonNode n, String idOverride) {
        String id = idOverride != null ? idOverride : text(n, "id", "q_" + UUID.randomUUID().toString().substring(0, 8));
        return new Question(new QuestionId(id), req(n, "title"), text(n, "description", ""), n.path("required").asBoolean(false),
                parseType(n.path("type")), n.hasNonNull("visibleIf") ? parseCondition(n.get("visibleIf")) : null,
                n.hasNonNull("scoring") ? parseScoring(n.get("scoring")) : null, text(n, "explanation", ""));
    }

    public static QuestionType parseType(JsonNode n) {
        String kind = req(n, "kind");
        return switch (kind) {
            case "short_text" -> new QuestionType.ShortText(n.path("maxLength").asInt(0), n.path("pattern").asText(null));
            case "long_text" -> new QuestionType.LongText(n.path("maxLength").asInt(0));
            case "number" -> new QuestionType.NumberInput(dbl(n, "min"), dbl(n, "max"), n.path("integerOnly").asBoolean(false));
            case "date" -> new QuestionType.DateInput(date(n, "min"), date(n, "max"));
            case "yes_no" -> new QuestionType.YesNo();
            case "single_choice" -> new QuestionType.SingleChoice(options(n.path("options")), n.path("allowOther").asBoolean(false));
            case "multi_choice" -> new QuestionType.MultiChoice(options(n.path("options")), n.path("minSelections").asInt(0),
                    n.hasNonNull("maxSelections") ? n.get("maxSelections").asInt() : null, n.path("allowOther").asBoolean(false));
            case "rating" -> new QuestionType.Rating(n.path("max").asInt(5));
            case "scale" -> new QuestionType.Scale(n.path("min").asInt(1), n.path("max").asInt(10),
                    text(n, "minLabel", ""), text(n, "maxLabel", ""));
            case "ranking" -> new QuestionType.Ranking(options(n.path("options")), n.hasNonNull("topN") ? n.get("topN").asInt() : null);
            case "matrix" -> new QuestionType.Matrix(options(n.path("rows")), options(n.path("columns")),
                    n.path("multiplePerRow").asBoolean(false), n.path("requireAllRows").asBoolean(false));
            default -> throw bad("Unknown question kind: " + kind);
        };
    }

    public static Condition parseCondition(JsonNode n) {
        String op = req(n, "op");
        return switch (op) {
            case "always" -> Condition.ALWAYS;
            case "answered" -> new Condition.Answered(qid(n));
            case "choice_selected" -> new Condition.ChoiceSelected(qid(n), new OptionId(req(n, "option")));
            case "compare" -> new Condition.Compare(qid(n), CompareOp.valueOf(req(n, "cmp").toUpperCase()), n.path("value").asDouble());
            case "bool_is" -> new Condition.BoolIs(qid(n), n.path("value").asBoolean());
            case "text_contains" -> new Condition.TextContains(qid(n), req(n, "value"));
            case "and" -> new Condition.And(list(n.path("conditions"), SurveyJson::parseCondition));
            case "or" -> new Condition.Or(list(n.path("conditions"), SurveyJson::parseCondition));
            case "not" -> new Condition.Not(parseCondition(n.path("condition")));
            default -> throw bad("Unknown condition op: " + op);
        };
    }

    public static Scoring parseScoring(JsonNode n) {
        String kind = req(n, "kind");
        double points = n.path("points").asDouble(1);
        return switch (kind) {
            case "none" -> Scoring.NONE;
            case "exact_choice" -> new Scoring.ExactChoice(optionIds(n.path("correct")), points);
            case "partial_choices" -> new Scoring.PartialChoices(optionIds(n.path("correct")), points, n.path("penalizeWrong").asBoolean(false));
            case "text_match" -> new Scoring.TextMatch(new LinkedHashSet<>(list(n.path("accepted"), JsonNode::asText)),
                    n.path("ignoreCase").asBoolean(true), points);
            case "numeric_match" -> new Scoring.NumericMatch(n.path("expected").asDouble(), n.path("tolerance").asDouble(0), points);
            case "bool_match" -> new Scoring.BoolMatch(n.path("expected").asBoolean(), points);
            case "order_match" -> new Scoring.OrderMatch(list(n.path("correctOrder"), x -> new OptionId(x.asText())), points);
            case "matrix_match" -> {
                Map<OptionId, Set<OptionId>> m = new LinkedHashMap<>();
                n.path("expected").fields().forEachRemaining(e -> m.put(new OptionId(e.getKey()), optionIds(e.getValue())));
                yield new Scoring.MatrixMatch(m, points);
            }
            default -> throw bad("Unknown scoring kind: " + kind);
        };
    }

    public static AnswerValue parseAnswer(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        if (n.has("text")) return new AnswerValue.Text(n.get("text").asText());
        if (n.has("number")) return new AnswerValue.Numeric(n.get("number").asDouble());
        if (n.has("date")) return new AnswerValue.Day(LocalDate.parse(n.get("date").asText()));
        if (n.has("bool")) return new AnswerValue.Bool(n.get("bool").asBoolean());
        if (n.has("choice") || n.has("other"))
            return new AnswerValue.Choice(optionIds(n.path("choice")), n.path("other").asText(""));
        if (n.has("ranking")) return new AnswerValue.Ranking(list(n.get("ranking"), x -> new OptionId(x.asText())));
        if (n.has("matrix")) {
            Map<OptionId, Set<OptionId>> m = new LinkedHashMap<>();
            n.get("matrix").fields().forEachRemaining(e -> m.put(new OptionId(e.getKey()), optionIds(e.getValue())));
            return new AnswerValue.Matrix(m);
        }
        throw bad("Answer must contain one of: text, number, date, bool, choice/other, ranking, matrix");
    }

    public static SurveySettings parseSettings(JsonNode n, SurveySettings base) {
        return new SurveySettings(n.path("anonymous").asBoolean(base.anonymous()),
                n.path("allowMultipleResponses").asBoolean(base.allowMultipleResponses()),
                n.path("maxResponses").asInt(base.maxResponses()),
                n.hasNonNull("opensAt") ? Instant.parse(n.get("opensAt").asText()) : base.opensAt(),
                n.hasNonNull("closesAt") ? Instant.parse(n.get("closesAt").asText()) : base.closesAt(),
                text(n, "thankYouMessage", base.thankYouMessage()));
    }

    public static QuizSettings parseQuiz(JsonNode n, QuizSettings base) {
        return new QuizSettings(n.path("passingPercent").asDouble(base.passingPercent()),
                n.hasNonNull("timeLimitSeconds") ? Duration.ofSeconds(n.get("timeLimitSeconds").asLong()) : base.timeLimit(),
                n.path("maxAttempts").asInt(base.maxAttempts()), n.path("shuffleQuestions").asBoolean(base.shuffleQuestions()),
                n.hasNonNull("resultsVisibility") ? ResultsVisibility.valueOf(n.get("resultsVisibility").asText().toUpperCase())
                        : base.resultsVisibility(),
                n.path("showCorrectAnswers").asBoolean(base.showCorrectAnswers()));
    }

    // =============================================================== rendering

    public static Map<String, Object> render(Survey s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id().value());
        m.put("kind", s.kind());
        m.put("status", s.status());
        m.put("version", s.version());
        s.previousVersion().ifPresent(p -> m.put("previousVersion", p.value()));
        m.put("title", s.title());
        m.put("description", s.description());
        m.put("settings", renderSettings(s.settings()));
        s.quizSettings().ifPresent(q -> m.put("quiz", renderQuiz(q)));
        m.put("sections", s.sections().stream().map(SurveyJson::renderSection).toList());
        return m;
    }

    public static Map<String, Object> renderSummary(Survey s) {
        return linked("id", s.id().value(), "title", s.title(), "kind", s.kind(), "status", s.status(),
                "version", s.version(), "questions", s.questionCount());
    }

    public static Map<String, Object> renderSection(Section sec) {
        return linked("id", sec.id().value(), "title", sec.title(), "description", sec.description(),
                "questions", sec.questions().stream().map(q -> renderQuestion(q, true)).toList());
    }

    /** @param includeAnswerKey false when rendering for respondents (never leak scoring/explanations before submit) */
    public static Map<String, Object> renderQuestion(Question q, boolean includeAnswerKey) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.id().value());
        m.put("title", q.title());
        m.put("description", q.description());
        m.put("required", q.required());
        m.put("type", renderType(q.type()));
        if (includeAnswerKey) {
            m.put("visibleIf", renderCondition(q.visibleIf()));
            m.put("scoring", renderScoring(q.scoring()));
            m.put("explanation", q.explanation());
        }
        return m;
    }

    static Map<String, Object> renderType(QuestionType t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", t.kind());
        switch (t) {
            case QuestionType.ShortText x -> { m.put("maxLength", x.maxLength()); m.put("pattern", x.pattern()); }
            case QuestionType.LongText x -> m.put("maxLength", x.maxLength());
            case QuestionType.NumberInput x -> { m.put("min", x.min()); m.put("max", x.max()); m.put("integerOnly", x.integerOnly()); }
            case QuestionType.DateInput x -> { m.put("min", str(x.min())); m.put("max", str(x.max())); }
            case QuestionType.YesNo x -> { }
            case QuestionType.SingleChoice x -> { m.put("options", renderOptions(x.options())); m.put("allowOther", x.allowOther()); }
            case QuestionType.MultiChoice x -> {
                m.put("options", renderOptions(x.options())); m.put("minSelections", x.minSelections());
                m.put("maxSelections", x.maxSelections()); m.put("allowOther", x.allowOther());
            }
            case QuestionType.Rating x -> m.put("max", x.max());
            case QuestionType.Scale x -> {
                m.put("min", x.min()); m.put("max", x.max()); m.put("minLabel", x.minLabel()); m.put("maxLabel", x.maxLabel());
            }
            case QuestionType.Ranking x -> { m.put("options", renderOptions(x.options())); m.put("topN", x.topN()); }
            case QuestionType.Matrix x -> {
                m.put("rows", renderOptions(x.rows())); m.put("columns", renderOptions(x.columns()));
                m.put("multiplePerRow", x.multiplePerRow()); m.put("requireAllRows", x.requireAllRows());
            }
        }
        return m;
    }

    static Map<String, Object> renderCondition(Condition c) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (c) {
            case Condition.Always x -> m.put("op", "always");
            case Condition.Answered x -> { m.put("op", "answered"); m.put("question", x.question().value()); }
            case Condition.ChoiceSelected x -> {
                m.put("op", "choice_selected"); m.put("question", x.question().value()); m.put("option", x.option().value());
            }
            case Condition.Compare x -> {
                m.put("op", "compare"); m.put("question", x.question().value());
                m.put("cmp", x.op().name().toLowerCase()); m.put("value", x.value());
            }
            case Condition.BoolIs x -> { m.put("op", "bool_is"); m.put("question", x.question().value()); m.put("value", x.expected()); }
            case Condition.TextContains x -> { m.put("op", "text_contains"); m.put("question", x.question().value()); m.put("value", x.needle()); }
            case Condition.And x -> { m.put("op", "and"); m.put("conditions", x.all().stream().map(SurveyJson::renderCondition).toList()); }
            case Condition.Or x -> { m.put("op", "or"); m.put("conditions", x.any().stream().map(SurveyJson::renderCondition).toList()); }
            case Condition.Not x -> { m.put("op", "not"); m.put("condition", renderCondition(x.inner())); }
        }
        return m;
    }

    static Map<String, Object> renderScoring(Scoring s) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (s) {
            case Scoring.None x -> m.put("kind", "none");
            case Scoring.ExactChoice x -> { m.put("kind", "exact_choice"); m.put("correct", ids(x.correct())); m.put("points", x.points()); }
            case Scoring.PartialChoices x -> {
                m.put("kind", "partial_choices"); m.put("correct", ids(x.correct()));
                m.put("points", x.points()); m.put("penalizeWrong", x.penalizeWrong());
            }
            case Scoring.TextMatch x -> {
                m.put("kind", "text_match"); m.put("accepted", x.accepted()); m.put("ignoreCase", x.ignoreCase()); m.put("points", x.points());
            }
            case Scoring.NumericMatch x -> {
                m.put("kind", "numeric_match"); m.put("expected", x.expected()); m.put("tolerance", x.tolerance()); m.put("points", x.points());
            }
            case Scoring.BoolMatch x -> { m.put("kind", "bool_match"); m.put("expected", x.expected()); m.put("points", x.points()); }
            case Scoring.OrderMatch x -> {
                m.put("kind", "order_match"); m.put("correctOrder", x.correctOrder().stream().map(OptionId::value).toList());
                m.put("points", x.points());
            }
            case Scoring.MatrixMatch x -> {
                Map<String, Object> exp = new LinkedHashMap<>();
                x.expected().forEach((k, v) -> exp.put(k.value(), ids(v)));
                m.put("kind", "matrix_match"); m.put("expected", exp); m.put("points", x.points());
            }
        }
        return m;
    }

    static Map<String, Object> renderSettings(SurveySettings s) {
        return linked("anonymous", s.anonymous(), "allowMultipleResponses", s.allowMultipleResponses(),
                "maxResponses", s.maxResponses(), "opensAt", str(s.opensAt()), "closesAt", str(s.closesAt()),
                "thankYouMessage", s.thankYouMessage());
    }

    static Map<String, Object> renderQuiz(QuizSettings q) {
        return linked("passingPercent", q.passingPercent(), "timeLimitSeconds", q.timeLimit().toSeconds(),
                "maxAttempts", q.maxAttempts(), "shuffleQuestions", q.shuffleQuestions(),
                "resultsVisibility", q.resultsVisibility(), "showCorrectAnswers", q.showCorrectAnswers());
    }

    public static Map<String, Object> renderAnswer(AnswerValue a) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (a) {
            case AnswerValue.Text x -> m.put("text", x.value());
            case AnswerValue.Numeric x -> m.put("number", x.value());
            case AnswerValue.Day x -> m.put("date", x.value().toString());
            case AnswerValue.Bool x -> m.put("bool", x.value());
            case AnswerValue.Choice x -> { m.put("choice", ids(x.selected())); if (!x.otherText().isBlank()) m.put("other", x.otherText()); }
            case AnswerValue.Ranking x -> m.put("ranking", x.order().stream().map(OptionId::value).toList());
            case AnswerValue.Matrix x -> {
                Map<String, Object> cells = new LinkedHashMap<>();
                x.cells().forEach((k, v) -> cells.put(k.value(), ids(v)));
                m.put("matrix", cells);
            }
        }
        return m;
    }

    // =============================================================== helpers

    private static List<Map<String, Object>> renderOptions(List<Option> os) {
        return os.stream().map(o -> linked("id", o.id().value(), "label", o.label())).toList();
    }

    private static List<String> ids(Collection<OptionId> c) { return c.stream().map(OptionId::value).toList(); }

    private static List<Option> options(JsonNode arr) {
        return list(arr, o -> new Option(new OptionId(o.hasNonNull("id") ? o.get("id").asText()
                : req(o, "label").toLowerCase().replaceAll("[^a-z0-9]+", "_")), req(o, "label")));
    }

    private static Set<OptionId> optionIds(JsonNode arr) {
        return new LinkedHashSet<>(list(arr, x -> new OptionId(x.asText())));
    }

    private static <T> List<T> list(JsonNode arr, java.util.function.Function<JsonNode, T> f) {
        List<T> out = new ArrayList<>();
        if (arr != null && arr.isArray()) arr.forEach(x -> out.add(f.apply(x)));
        return out;
    }

    private static QuestionId qid(JsonNode n) { return new QuestionId(req(n, "question")); }

    private static String req(JsonNode n, String field) {
        if (n == null || !n.hasNonNull(field)) throw bad("Missing field: " + field);
        return n.get(field).asText();
    }

    private static String text(JsonNode n, String field, String dflt) { return n.hasNonNull(field) ? n.get(field).asText() : dflt; }

    private static Double dbl(JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asDouble() : null; }

    private static LocalDate date(JsonNode n, String f) { return n.hasNonNull(f) ? LocalDate.parse(n.get(f).asText()) : null; }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static DomainException bad(String msg) { return new DomainException(ErrorCode.VALIDATION_FAILED, msg); }

    private static Map<String, Object> linked(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
