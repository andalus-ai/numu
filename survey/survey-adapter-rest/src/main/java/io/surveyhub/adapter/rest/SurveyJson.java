package io.surveyhub.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.surveyhub.domain.DomainException;
import io.surveyhub.domain.ErrorCode;
import io.surveyhub.domain.answer.AnswerValue;
import io.surveyhub.domain.id.OptionId;
import io.surveyhub.domain.id.QuestionId;
import io.surveyhub.domain.logic.CompareOp;
import io.surveyhub.domain.logic.Condition;
import io.surveyhub.domain.question.Option;
import io.surveyhub.domain.question.Question;
import io.surveyhub.domain.question.QuestionType;
import io.surveyhub.domain.quiz.QuizSettings;
import io.surveyhub.domain.quiz.ResultsVisibility;
import io.surveyhub.domain.quiz.ScoreBand;
import io.surveyhub.domain.quiz.Scoring;
import io.surveyhub.domain.survey.Eligibility;
import io.surveyhub.domain.survey.IndexDefinition;
import io.surveyhub.domain.survey.Presentation;
import io.surveyhub.domain.survey.Translations;
import io.surveyhub.domain.survey.Section;
import io.surveyhub.domain.survey.Survey;
import io.surveyhub.domain.survey.SurveySettings;

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
                n.hasNonNull("scoring") ? parseScoring(n.get("scoring")) : null, text(n, "explanation", ""),
                n.path("randomizeOptions").asBoolean(false));
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
            case "formatted_text" -> new QuestionType.FormattedText(
                    QuestionType.FormattedText.Format.valueOf(text(n, "format", "EMAIL").toUpperCase()));
            case "slider" -> new QuestionType.Slider(n.path("min").asDouble(0), n.path("max").asDouble(100),
                    n.path("step").asDouble(1), text(n, "unit", ""));
            case "constant_sum" -> new QuestionType.ConstantSum(options(n.path("options")), n.path("total").asDouble(100));
            case "consent" -> new QuestionType.Consent(text(n, "statement", ""));
            case "statement" -> new QuestionType.Statement(text(n, "text", ""));
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
            case "option_points" -> {
                Map<OptionId, Double> pts = new LinkedHashMap<>();
                n.path("points").fields().forEachRemaining(e -> pts.put(new OptionId(e.getKey()), e.getValue().asDouble()));
                yield new Scoring.OptionPoints(pts, n.path("multi").asBoolean(false));
            }
            case "matrix_match" -> {
                Map<OptionId, Set<OptionId>> m = new LinkedHashMap<>();
                n.path("expected").fields().forEachRemaining(e -> m.put(new OptionId(e.getKey()), optionIds(e.getValue())));
                yield new Scoring.MatrixMatch(m, points);
            }
            default -> throw bad("Unknown scoring kind: " + kind);
        };
    }

    public static AnswerValue parseAnswer(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode() || n.size() == 0) return null; // {} = clear the answer
        if (n.has("text")) return new AnswerValue.Text(n.get("text").asText());
        if (n.has("number")) return new AnswerValue.Numeric(n.get("number").asDouble());
        if (n.has("date")) return new AnswerValue.Day(LocalDate.parse(n.get("date").asText()));
        if (n.has("bool")) return new AnswerValue.Bool(n.get("bool").asBoolean());
        if (n.has("choice") || n.has("other"))
            return new AnswerValue.Choice(optionIds(n.path("choice")), n.path("other").asText(""));
        if (n.has("ranking")) return new AnswerValue.Ranking(list(n.get("ranking"), x -> new OptionId(x.asText())));
        if (n.has("allocation")) {
            Map<OptionId, Double> m = new LinkedHashMap<>();
            n.get("allocation").fields().forEachRemaining(e -> m.put(new OptionId(e.getKey()), e.getValue().asDouble()));
            return new AnswerValue.Allocation(m);
        }
        if (n.has("matrix")) {
            Map<OptionId, Set<OptionId>> m = new LinkedHashMap<>();
            n.get("matrix").fields().forEachRemaining(e -> m.put(new OptionId(e.getKey()), optionIds(e.getValue())));
            return new AnswerValue.Matrix(m);
        }
        throw bad("Answer must contain one of: text, number, date, bool, choice/other, ranking, matrix, allocation");
    }

    public static SurveySettings parseSettings(JsonNode n, SurveySettings base) {
        return new SurveySettings(n.path("anonymous").asBoolean(base.anonymous()),
                n.path("allowMultipleResponses").asBoolean(base.allowMultipleResponses()),
                n.path("maxResponses").asInt(base.maxResponses()),
                n.hasNonNull("opensAt") ? Instant.parse(n.get("opensAt").asText()) : base.opensAt(),
                n.hasNonNull("closesAt") ? Instant.parse(n.get("closesAt").asText()) : base.closesAt(),
                text(n, "thankYouMessage", base.thankYouMessage()), n.path("publicResults").asBoolean(base.publicResults()));
    }

    /** Body shape: {"defaultLocale":"en","locales":{"id":{"survey.title":"...","question.q1.title":"..."}}} */
    public static Translations parseTranslations(JsonNode n, Translations base) {
        Map<String, Map<String, String>> by = new LinkedHashMap<>();
        if (n.has("locales")) {
            n.get("locales").fields().forEachRemaining(e -> {
                Map<String, String> texts = new LinkedHashMap<>();
                e.getValue().fields().forEachRemaining(t -> texts.put(t.getKey(), t.getValue().asText()));
                by.put(e.getKey(), texts);
            });
        } else {
            by.putAll(base.byLocale());
        }
        return new Translations(text(n, "defaultLocale", base.defaultLocale()), by);
    }

    public static Map<String, Object> renderTranslations(Translations t) {
        return linked("defaultLocale", t.defaultLocale(), "locales", t.byLocale());
    }

    public static Eligibility parseEligibility(JsonNode n) {
        return new Eligibility(
                list(n.path("screenOuts"), s -> new Eligibility.ScreenOut(parseCondition(s.path("when")), text(s, "message", ""))),
                list(n.path("quotas"), q -> new Eligibility.Quota(req(q, "name"), qid(q), new OptionId(req(q, "option")), q.path("limit").asInt(0))));
    }

    public static List<IndexDefinition> parseIndices(JsonNode arr) {
        return list(arr, d -> new IndexDefinition(req(d, "name"), list(d.path("items"), it -> new IndexDefinition.Item(
                qid(it), text(it, "row", ""), it.path("weight").asDouble(1), it.path("reverse").asBoolean(false)))));
    }

    public static Map<String, Object> renderEligibility(Eligibility e) {
        return linked("screenOuts", e.screenOuts().stream().map(s -> linked("when", renderCondition(s.when()), "message", s.message())).toList(),
                "quotas", e.quotas().stream().map(q -> linked("name", q.name(), "question", q.question().value(),
                        "option", q.option().value(), "limit", q.limit())).toList());
    }

    public static List<Map<String, Object>> renderIndices(List<IndexDefinition> defs) {
        return defs.stream().map(d -> linked("name", d.name(), "items", d.items().stream().map(i -> linked(
                "question", i.question().value(), "row", i.row(), "weight", i.weight(), "reverse", i.reverse())).toList())).toList();
    }

    public static Presentation parsePresentation(JsonNode n, Presentation base) {
        return new Presentation(text(n, "primaryColor", base.primaryColor()), text(n, "backgroundColor", base.backgroundColor()),
                text(n, "textColor", base.textColor()), text(n, "fontFamily", base.fontFamily()), text(n, "logoUrl", base.logoUrl()),
                n.hasNonNull("layout") ? Presentation.Layout.valueOf(n.get("layout").asText().toUpperCase()) : base.layout(),
                n.path("showProgress").asBoolean(base.showProgress()), text(n, "welcomeMessage", base.welcomeMessage()));
    }

    public static QuizSettings parseQuiz(JsonNode n, QuizSettings base) {
        return new QuizSettings(n.path("passingPercent").asDouble(base.passingPercent()),
                n.hasNonNull("timeLimitSeconds") ? Duration.ofSeconds(n.get("timeLimitSeconds").asLong()) : base.timeLimit(),
                n.path("maxAttempts").asInt(base.maxAttempts()), n.path("shuffleQuestions").asBoolean(base.shuffleQuestions()),
                n.hasNonNull("resultsVisibility") ? ResultsVisibility.valueOf(n.get("resultsVisibility").asText().toUpperCase())
                        : base.resultsVisibility(),
                n.path("showCorrectAnswers").asBoolean(base.showCorrectAnswers()),
                n.has("bands") ? list(n.get("bands"), b -> new ScoreBand(b.path("minPercent").asDouble(0), req(b, "label"), text(b, "message", ""))) : base.bands());
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
        m.put("access", s.accessMode());
        s.shareToken().ifPresent(t -> m.put("shareToken", t));
        m.put("presentation", renderPresentation(s.presentation()));
        m.put("translations", renderTranslations(s.translations()));
        m.put("eligibility", renderEligibility(s.eligibility()));
        m.put("indices", renderIndices(s.indices()));
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
        m.put("randomizeOptions", q.randomizeOptions());
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
            case QuestionType.FormattedText x -> m.put("format", x.format().name());
            case QuestionType.Slider x -> { m.put("min", x.min()); m.put("max", x.max()); m.put("step", x.step()); m.put("unit", x.unit()); }
            case QuestionType.ConstantSum x -> { m.put("options", renderOptions(x.options())); m.put("total", x.total()); }
            case QuestionType.Consent x -> m.put("statement", x.statement());
            case QuestionType.Statement x -> m.put("text", x.text());
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
            case Scoring.OptionPoints x -> {
                Map<String, Object> pts = new LinkedHashMap<>();
                x.points().forEach((k, v) -> pts.put(k.value(), v));
                m.put("kind", "option_points"); m.put("points", pts); m.put("multi", x.multi());
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
                "thankYouMessage", s.thankYouMessage(), "publicResults", s.publicResults());
    }

    static Map<String, Object> renderPresentation(Presentation p) {
        return linked("primaryColor", p.primaryColor(), "backgroundColor", p.backgroundColor(), "textColor", p.textColor(),
                "fontFamily", p.fontFamily(), "logoUrl", p.logoUrl(), "layout", p.layout(), "showProgress", p.showProgress(),
                "welcomeMessage", p.welcomeMessage());
    }

    static Map<String, Object> renderQuiz(QuizSettings q) {
        return linked("passingPercent", q.passingPercent(), "timeLimitSeconds", q.timeLimit().toSeconds(),
                "maxAttempts", q.maxAttempts(), "shuffleQuestions", q.shuffleQuestions(),
                "resultsVisibility", q.resultsVisibility(), "showCorrectAnswers", q.showCorrectAnswers(),
                "bands", q.bands().stream().map(b -> linked("minPercent", b.minPercent(), "label", b.label(), "message", b.message())).toList());
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
            case AnswerValue.Allocation x -> {
                Map<String, Object> shares = new LinkedHashMap<>();
                x.shares().forEach((k, v) -> shares.put(k.value(), v));
                m.put("allocation", shares);
            }
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
