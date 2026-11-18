package tech.kayys.numou.feedback.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.OptionId;

import java.time.LocalDate;
import java.util.*;

/** Strict JSON-to-answer codec for the public respondent API. */
final class SurveyAnswerJson {
    private SurveyAnswerJson() {}

    static AnswerValue parse(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Answer must be a JSON object");
        String kind = requiredText(node, "kind");
        return switch (kind) {
            case "text" -> new AnswerValue.Text(requiredText(node, "value"));
            case "number" -> {
                JsonNode value = node.get("value");
                if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble()))
                    throw new IllegalArgumentException("Numeric answer value must be finite");
                yield new AnswerValue.Numeric(value.asDouble());
            }
            case "day" -> {
                try { yield new AnswerValue.Day(LocalDate.parse(requiredText(node, "value"))); }
                catch (java.time.DateTimeException invalidDate) {
                    throw new IllegalArgumentException("Day answer must use ISO-8601 date format", invalidDate);
                }
            }
            case "bool" -> {
                JsonNode value = node.get("value");
                if (value == null || !value.isBoolean()) throw new IllegalArgumentException("Boolean answer value is required");
                yield new AnswerValue.Bool(value.asBoolean());
            }
            case "choice" -> {
                JsonNode selected = node.path("selected");
                if (!selected.isArray()) throw new IllegalArgumentException("Choice selected must be an array");
                Set<OptionId> ids = new LinkedHashSet<>();
                selected.forEach(v -> {
                    if (!v.isTextual() || v.asText().isBlank()) throw new IllegalArgumentException("Choice option IDs must be nonblank strings");
                    ids.add(new OptionId(v.asText()));
                });
                yield new AnswerValue.Choice(ids, node.path("otherText").asText(""));
            }
            case "ranking" -> {
                JsonNode order = node.path("order");
                if (!order.isArray()) throw new IllegalArgumentException("Ranking order must be an array");
                List<OptionId> ids = new ArrayList<>();
                order.forEach(v -> {
                    if (!v.isTextual() || v.asText().isBlank()) throw new IllegalArgumentException("Ranking option IDs must be nonblank strings");
                    ids.add(new OptionId(v.asText()));
                });
                yield new AnswerValue.Ranking(ids);
            }
            case "matrix" -> {
                JsonNode cells = node.path("cells");
                if (!cells.isObject()) throw new IllegalArgumentException("Matrix cells must be an object");
                Map<OptionId, Set<OptionId>> matrix = new LinkedHashMap<>();
                cells.fields().forEachRemaining(row -> {
                    if (!row.getValue().isArray()) throw new IllegalArgumentException("Matrix row values must be arrays");
                    Set<OptionId> columns = new LinkedHashSet<>();
                    row.getValue().forEach(v -> {
                        if (!v.isTextual() || v.asText().isBlank()) throw new IllegalArgumentException("Matrix option IDs must be nonblank strings");
                        columns.add(new OptionId(v.asText()));
                    });
                    matrix.put(new OptionId(row.getKey()), columns);
                });
                yield new AnswerValue.Matrix(matrix);
            }
            default -> throw new IllegalArgumentException("Unsupported answer kind: " + kind);
        };
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw new IllegalArgumentException(field + " must be a nonblank string");
        return value.asText();
    }
}
