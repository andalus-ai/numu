package io.surveyhub.presentation;

import io.surveyhub.domain.analytics.*;
import io.surveyhub.domain.question.Question;
import io.surveyhub.domain.question.QuestionType;
import io.surveyhub.domain.survey.Survey;

import java.util.*;

/**
 * Self-contained analytics report (HTML + CSS bars, no scripts). Print it to PDF from any browser, or e-mail it.
 */
public final class ReportPage {
    private static final String CSS = """
            body{font-family:system-ui,sans-serif;max-width:900px;margin:24px auto;padding:0 16px;color:#111827}
            h1{margin:0}h2{margin:32px 0 8px;border-bottom:2px solid #e5e7eb;padding-bottom:4px}h3{margin:0 0 6px;font-size:1rem}
            .muted{color:#6b7280;font-size:.85rem}.kpis{display:flex;gap:12px;flex-wrap:wrap}
            .kpi{flex:1;min-width:140px;border:1px solid #e5e7eb;border-radius:10px;padding:10px 14px}.kpi b{display:block;font-size:1.5rem}
            .q{border:1px solid #e5e7eb;border-radius:10px;padding:12px 16px;margin:12px 0;break-inside:avoid}
            .bar{background:#f3f4f6;border-radius:4px;margin:3px 0;position:relative;height:22px}
            .bar>span{position:absolute;inset:0 auto 0 0;background:#2563eb;opacity:.55;border-radius:4px}
            .bar>em{position:relative;padding:0 8px;font-style:normal;line-height:22px;font-size:.82rem}
            table{border-collapse:collapse;width:100%;font-size:.88rem}th,td{border-bottom:1px solid #e5e7eb;padding:4px 8px;text-align:left}
            td.n,th.n{text-align:right}.chip{display:inline-block;background:#eef2ff;border-radius:999px;padding:1px 10px;margin:2px;font-size:.8rem}
            @media print{body{margin:0}.q{break-inside:avoid}}
            """;

    private ReportPage() {}

    public static String render(ReportData d) {
        Survey s = d.survey();
        StringBuilder b = new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>")
                .append(Html.esc(s.title())).append(" - report</title><style>").append(CSS).append("</style></head><body>");
        b.append("<h1>").append(Html.esc(s.title())).append("</h1><div class=\"muted\">")
                .append(Html.esc(s.kind())).append(" · version ").append(s.version()).append(" · ")
                .append(Html.esc(s.status())).append(" · generated ").append(Html.esc(d.generatedAt())).append("</div>");

        Overview o = d.overview();
        b.append("<h2>Overview</h2><div class=\"kpis\">")
                .append(kpi("Started", o.started())).append(kpi("Submitted", o.submitted()))
                .append(kpi("Completion", o.completionRate() + "%"))
                .append(o.disqualified() > 0 ? kpi("Screened out", o.disqualified()) : "")
                .append(kpi("Avg. time", o.averageDurationSeconds() == null ? "–" : duration(o.averageDurationSeconds())))
                .append(kpi("Median time", o.medianDurationSeconds() == null ? "–" : duration(o.medianDurationSeconds())))
                .append("</div>");
        if (!o.submissionsPerDay().isEmpty()) {
            b.append("<h3 style=\"margin-top:16px\">Submissions per day</h3>");
            bars(b, new LinkedHashMap<>(o.submissionsPerDay()), null);
        }

        b.append("<h2>Drop-off</h2><table><tr><th>Question</th><th class=\"n\">Answered</th><th class=\"n\">% of started</th></tr>");
        for (Funnel.Step st : d.funnel().steps()) {
            b.append("<tr><td>").append(Html.esc(st.title())).append("</td><td class=\"n\">").append(st.answered())
                    .append("</td><td class=\"n\">").append(st.percentOfStarted()).append("%</td></tr>");
        }
        b.append("</table>");
        if (!d.funnel().abandonedAfter().isEmpty()) {
            b.append("<p class=\"muted\">Unfinished responses stopped after: ");
            d.funnel().abandonedAfter().forEach((q, n) -> b.append("<span class=\"chip\">").append(Html.esc(q)).append(" × ").append(n).append("</span>"));
            b.append("</p>");
        }

        if (!d.channels().isEmpty()) channels(b, d);
        if (!d.indices().isEmpty()) indices(b, d.indices());
        if (d.estimates() != null && d.estimates().n() > 0) estimates(b, d);
        d.ranked().forEach((qid, r) -> ranked(b, s, r));

        b.append("<h2>Results</h2>");
        Map<String, QuestionResult> byId = new HashMap<>();
        d.results().questions().forEach(r -> byId.put(r.question().value(), r));
        for (Question q : s.questions()) {
            if (q.type() instanceof QuestionType.Statement) continue;
            QuestionResult r = byId.get(q.id().value());
            b.append("<div class=\"q\"><h3>").append(Html.esc(q.title())).append("</h3><div class=\"muted\">")
                    .append(Html.esc(q.type().kind())).append(" · ").append(r.answered()).append(" answers</div>");
            question(b, q, r, d);
            b.append("</div>");
        }

        d.quiz().ifPresent(qz -> quiz(b, qz, d.leaderboard()));
        return b.append("</body></html>").toString();
    }

    // ------------------------------------------------------------------ question blocks

    @SuppressWarnings("unchecked")
    private static void question(StringBuilder b, Question q, QuestionResult r, ReportData d) {
        Map<String, Object> st = r.stats();
        Map<String, String> labels = Labels2.of(q);
        if (st.get("counts") instanceof Map<?, ?> counts) {
            Map<String, Number> m = new LinkedHashMap<>();
            ((Map<String, Long>) counts).forEach((k, v) -> m.put(labels.getOrDefault(k, k), v));
            bars(b, m, null);
            if (st.get("other") instanceof Long other && other > 0) b.append("<div class=\"muted\">\"Other\" answers: ").append(other).append("</div>");
        }
        if (st.get("distribution") instanceof Map<?, ?> dist) {
            Map<String, Number> m = new LinkedHashMap<>();
            ((Map<Long, Long>) dist).forEach((k, v) -> m.put(String.valueOf(k), v));
            bars(b, m, null);
        }
        if (st.containsKey("average")) {
            b.append("<div>Average <b>").append(st.get("average")).append("</b> (min ").append(st.get("min")).append(", max ")
                    .append(st.get("max")).append(")</div>");
        }
        if (st.containsKey("nps")) b.append("<div>NPS <b>").append(st.get("nps")).append("</b></div>");
        if (st.containsKey("yes")) {
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("Yes", (Number) st.get("yes"));
            m.put("No", (Number) st.get("no"));
            bars(b, m, null);
        }
        if (st.containsKey("accepted")) b.append("<div>Accepted: <b>").append(st.get("accepted")).append("</b></div>");
        if (st.get("averagePosition") instanceof Map<?, ?> pos) {
            b.append("<table><tr><th>Option</th><th class=\"n\">Avg. position</th></tr>");
            ((Map<String, Double>) pos).entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e ->
                    b.append("<tr><td>").append(Html.esc(labels.getOrDefault(e.getKey(), e.getKey()))).append("</td><td class=\"n\">")
                            .append(e.getValue()).append("</td></tr>"));
            b.append("</table>");
        }
        if (st.get("averageShare") instanceof Map<?, ?> shares) {
            Map<String, Number> m = new LinkedHashMap<>();
            ((Map<String, Double>) shares).forEach((k, v) -> m.put(labels.getOrDefault(k, k), v));
            bars(b, m, null);
        }
        if (st.get("cells") instanceof Map<?, ?> cells) {
            b.append("<table><tr><th>Row | column</th><th class=\"n\">Count</th></tr>");
            ((Map<String, Long>) cells).forEach((k, v) -> {
                String[] p = k.split("\\|", 2);
                b.append("<tr><td>").append(Html.esc(labels.getOrDefault(p[0], p[0]))).append(" | ")
                        .append(Html.esc(p.length > 1 ? labels.getOrDefault(p[1], p[1]) : "")).append("</td><td class=\"n\">").append(v).append("</td></tr>");
            });
            b.append("</table>");
        }
        List<Keyword> kws = d.keywords().get(q.id());
        if (kws != null && !kws.isEmpty()) {
            b.append("<div>Top words: ");
            kws.forEach(k -> b.append("<span class=\"chip\">").append(Html.esc(k.word())).append(" ").append(k.count()).append("</span>"));
            b.append("</div>");
        }
    }

    private static void channels(StringBuilder b, ReportData d) {
        b.append("<h2>Channels (").append(Html.esc(d.channelKey())).append(")</h2><table><tr><th>Source</th><th class=\"n\">Started</th>"
                + "<th class=\"n\">Submitted</th><th class=\"n\">Completion</th></tr>");
        for (ChannelStat c : d.channels()) {
            b.append("<tr><td>").append(Html.esc(c.value())).append("</td><td class=\"n\">").append(c.started())
                    .append("</td><td class=\"n\">").append(c.submitted()).append("</td><td class=\"n\">")
                    .append(c.completionRate()).append("%</td></tr>");
        }
        b.append("</table>");
    }

    private static void indices(StringBuilder b, List<IndexResult> indices) {
        b.append("<h2>Indices (0-100)</h2>");
        for (IndexResult i : indices) {
            b.append("<div class=\"q\"><h3>").append(Html.esc(i.name())).append("</h3><div class=\"kpis\">")
                    .append(kpi("Mean", i.mean())).append(kpi("Respondents", i.n())).append(kpi("Min", i.min())).append(kpi("Max", i.max()))
                    .append("</div>");
            bars(b, new LinkedHashMap<>(i.histogram()), null);
            if (!i.byGroup().isEmpty()) {
                b.append("<div class=\"muted\">Mean by group</div>");
                bars(b, new LinkedHashMap<>(i.byGroup()), null);
            }
            b.append("</div>");
        }
    }

    private static void estimates(StringBuilder b, ReportData d) {
        Estimates e = d.estimates();
        b.append("<h2>Estimates with 95% margin of error</h2><p class=\"muted\">n = ").append(e.n())
                .append(e.weighted() ? "; weighted (effective n " + e.effectiveN() + ", design effect " + e.designEffect() + ")" : "; unweighted")
                .append(". Margins are in percentage points and assume a random sample; online/opt-in samples carry additional, unmeasured error.</p>");
        Map<String, Question> byId = new HashMap<>();
        d.survey().questions().forEach(q -> byId.put(q.id().value(), q));
        for (Estimates.QuestionEstimate qe : e.questions()) {
            if (qe.base() == 0) continue;
            Map<String, String> labels = Labels.of(byId.get(qe.question().value()));
            b.append("<div class=\"q\"><h3>").append(Html.esc(qe.title())).append("</h3><div class=\"muted\">base ").append(qe.base())
                    .append("</div><table><tr><th>Option</th><th class=\"n\">%</th><th class=\"n\">± points</th></tr>");
            for (Estimates.OptionEstimate o : qe.options()) {
                b.append("<tr><td>").append(Html.esc(labels.getOrDefault(o.option(), o.option()))).append("</td><td class=\"n\">")
                        .append(o.percent()).append("</td><td class=\"n\">").append(o.marginOfError()).append("</td></tr>");
            }
            b.append("</table></div>");
        }
    }

    private static void ranked(StringBuilder b, Survey s, RankedChoiceResult r) {
        Question q = s.findQuestion(r.question()).orElseThrow();
        Map<String, String> labels = Labels.of(q);
        java.util.function.Function<String, String> name = id -> id == null ? "–" : labels.getOrDefault(id, id);
        b.append("<h2>Ranked choice: ").append(Html.esc(q.title())).append("</h2><p class=\"muted\">").append(r.ballots()).append(" ballots</p>");
        if (r.ballots() == 0) return;
        b.append("<div class=\"kpis\">").append(kpi("Instant-runoff winner", name.apply(r.winner())))
                .append(kpi("Condorcet winner", r.condorcetWinner() == null ? "none" : name.apply(r.condorcetWinner()))).append("</div>");
        b.append("<h3 style=\"margin-top:16px\">Rounds</h3><table><tr><th class=\"n\">Round</th><th>Tally</th><th>Eliminated</th><th class=\"n\">Exhausted</th></tr>");
        for (RankedChoiceResult.Round rd : r.rounds()) {
            StringBuilder tl = new StringBuilder();
            rd.tallies().forEach((k, v) -> tl.append(Html.esc(name.apply(k))).append(": ").append(v).append(" &nbsp; "));
            b.append("<tr><td class=\"n\">").append(rd.number()).append("</td><td>").append(tl).append("</td><td>")
                    .append(Html.esc(name.apply(rd.eliminated()))).append("</td><td class=\"n\">").append(rd.exhausted()).append("</td></tr>");
        }
        b.append("</table><h3 style=\"margin-top:16px\">Borda points</h3>");
        Map<String, Number> borda = new LinkedHashMap<>();
        r.borda().forEach((k, v) -> borda.put(name.apply(k), v));
        bars(b, borda, null);
    }

    private static void quiz(StringBuilder b, QuizItemAnalysis qz, List<LeaderboardEntry> board) {
        b.append("<h2>Quiz analysis</h2><div class=\"kpis\">").append(kpi("Attempts", qz.attempts()))
                .append(kpi("Mean score", qz.meanPercent() + "%")).append(kpi("Std. deviation", qz.stdDevPercent())).append("</div>");
        b.append("<h3 style=\"margin-top:16px\">Score distribution</h3>");
        bars(b, new LinkedHashMap<>(qz.histogram()), null);
        b.append("<h3 style=\"margin-top:16px\">Item analysis</h3><table><tr><th>Question</th><th class=\"n\">Correct %</th>"
                + "<th class=\"n\">Avg. points</th><th class=\"n\">Max</th><th class=\"n\">Discrimination</th></tr>");
        for (QuizItemAnalysis.Item i : qz.items()) {
            b.append("<tr><td>").append(Html.esc(i.title())).append("</td><td class=\"n\">").append(i.correctRate())
                    .append("</td><td class=\"n\">").append(i.averageEarned()).append("</td><td class=\"n\">").append(i.maxPoints())
                    .append("</td><td class=\"n\">").append(i.discrimination() == null ? "–" : i.discrimination()).append("</td></tr>");
        }
        b.append("</table><p class=\"muted\">Low correct % = hard question. Discrimination near 0 or negative means the question does not separate strong from weak performers.</p>");
        if (!board.isEmpty()) {
            b.append("<h3>Leaderboard</h3><table><tr><th class=\"n\">#</th><th>Respondent</th><th class=\"n\">Score</th><th class=\"n\">Time (s)</th></tr>");
            for (LeaderboardEntry e : board) {
                b.append("<tr><td class=\"n\">").append(e.rank()).append("</td><td>").append(Html.esc(e.respondent()))
                        .append("</td><td class=\"n\">").append(e.percent()).append("%</td><td class=\"n\">").append(e.durationSeconds()).append("</td></tr>");
            }
            b.append("</table>");
        }
    }

    // ------------------------------------------------------------------ primitives

    private static void bars(StringBuilder b, Map<String, ? extends Number> values, String unit) {
        double max = values.values().stream().mapToDouble(Number::doubleValue).max().orElse(1);
        if (max <= 0) max = 1;
        for (var e : values.entrySet()) {
            long pct = Math.round(100 * e.getValue().doubleValue() / max);
            b.append("<div class=\"bar\"><span style=\"width:").append(pct).append("%\"></span><em>")
                    .append(Html.esc(e.getKey())).append(" – ").append(e.getValue()).append(unit == null ? "" : unit).append("</em></div>");
        }
    }

    private static String kpi(String label, Object value) {
        return "<div class=\"kpi\"><b>" + Html.esc(value) + "</b>" + Html.esc(label) + "</div>";
    }

    private static String duration(double seconds) {
        long s = Math.round(seconds);
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    /** Label lookup incl. Yes/No; kept private to the report. */
    private static final class Labels2 {
        static Map<String, String> of(Question q) { return Labels.of(q); }
    }
}
