package io.surveyhub.presentation;

import io.surveyhub.application.port.in.AnalyticsUseCase.Dataset;
import io.surveyhub.domain.answer.AnswerValue;
import io.surveyhub.domain.question.Question;
import io.surveyhub.domain.question.QuestionType;
import io.surveyhub.domain.response.Response;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** One row per response, one column per question (RFC 4180 quoting, spreadsheet-formula injection neutralised). */
public final class CsvExport {
    private CsvExport() {}

    public static String render(Dataset data) {
        List<Question> qs = data.survey().questions().stream()
                .filter(q -> !(q.type() instanceof QuestionType.Statement)).toList();
        StringBuilder out = new StringBuilder();
        List<String> head = new ArrayList<>(List.of("response_id", "respondent", "status", "attempt", "started_at",
                "submitted_at", "duration_seconds", "score_percent", "passed", "band", "campaign_tags", "screen_out_reason"));
        qs.forEach(q -> head.add(q.title() + " [" + q.id() + "]"));
        row(out, head);

        List<Response> sorted = data.responses().stream().sorted(Comparator.comparing(Response::startedAt)).toList();
        for (Response r : sorted) {
            List<String> cells = new ArrayList<>();
            cells.add(r.id().value());
            cells.add(data.survey().settings().anonymous() ? "" : r.respondentId().orElse(""));
            cells.add(r.status().name());
            cells.add(String.valueOf(r.attempt()));
            cells.add(r.startedAt().toString());
            cells.add(r.submittedAt().map(Object::toString).orElse(""));
            cells.add(r.submittedAt().map(t -> String.valueOf(Duration.between(r.startedAt(), t).toSeconds())).orElse(""));
            cells.add(r.score().map(s -> String.valueOf(s.percent())).orElse(""));
            cells.add(r.score().map(s -> String.valueOf(s.passed())).orElse(""));
            cells.add(r.score().map(s -> s.band()).orElse(""));
            cells.add(r.metadata().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
            cells.add(r.disqualification().orElse(""));
            for (Question q : qs) cells.add(flatten(q, r.answers().get(q.id())));
            row(out, cells);
        }
        return out.toString();
    }

    static String flatten(Question q, AnswerValue a) {
        if (a == null) return "";
        Map<String, String> labels = Labels.of(q);
        return switch (a) {
            case AnswerValue.Text t -> t.value();
            case AnswerValue.Numeric n -> n.value() == Math.rint(n.value()) ? String.valueOf((long) n.value()) : String.valueOf(n.value());
            case AnswerValue.Day d -> d.value().toString();
            case AnswerValue.Bool b -> String.valueOf(b.value());
            case AnswerValue.Choice c -> {
                List<String> parts = c.selected().stream().map(id -> Labels.label(labels, id)).sorted().collect(Collectors.toList());
                if (!c.otherText().isBlank()) parts.add("other: " + c.otherText());
                yield String.join("; ", parts);
            }
            case AnswerValue.Ranking r -> r.order().stream().map(id -> Labels.label(labels, id)).collect(Collectors.joining(" > "));
            case AnswerValue.Matrix m -> m.cells().entrySet().stream().map(e -> Labels.label(labels, e.getKey()) + "="
                    + e.getValue().stream().map(id -> Labels.label(labels, id)).sorted().collect(Collectors.joining("|")))
                    .collect(Collectors.joining("; "));
            case AnswerValue.Allocation al -> al.shares().entrySet().stream()
                    .map(e -> Labels.label(labels, e.getKey()) + "=" + trim(e.getValue())).collect(Collectors.joining("; "));
        };
    }

    private static String trim(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    private static void row(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) out.append(',');
            out.append(cell(cells.get(i)));
        }
        out.append("\r\n");
    }

    /** Quotes when needed and defuses cells a spreadsheet would execute as a formula. */
    static String cell(String raw) {
        String v = raw == null ? "" : raw;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) v = "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }
}
