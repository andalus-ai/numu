package io.surveyhub.adapter.rest;

import io.surveyhub.domain.analytics.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plain-map JSON for analytics (domain ids are records, so we flatten them explicitly). */
final class AnalyticsJson {
    private AnalyticsJson() {}

    static Map<String, Object> results(SurveyResults r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("surveyId", r.surveyId().value());
        out.put("totalSubmitted", r.totalSubmitted());
        out.put("questions", r.questions().stream().map(q -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.question().value());
            m.put("title", q.title());
            m.put("kind", q.kind());
            m.put("answered", q.answered());
            m.put("stats", q.stats());
            return m;
        }).toList());
        r.quiz().ifPresent(q -> out.put("quiz", quizStats(q)));
        return out;
    }

    static Map<String, Object> quizStats(QuizStats q) {
        Map<String, Object> rate = new LinkedHashMap<>();
        q.correctRate().forEach((k, v) -> rate.put(k.value(), v));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attempts", q.attempts());
        m.put("averagePercent", q.averagePercent());
        m.put("passRate", q.passRate());
        m.put("highestPercent", q.highestPercent());
        m.put("lowestPercent", q.lowestPercent());
        m.put("correctRate", rate);
        return m;
    }

    static Map<String, Object> overview(Overview o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("started", o.started());
        m.put("submitted", o.submitted());
        m.put("inProgress", o.inProgress());
        m.put("disqualified", o.disqualified());
        m.put("completionRate", o.completionRate());
        m.put("averageDurationSeconds", o.averageDurationSeconds());
        m.put("medianDurationSeconds", o.medianDurationSeconds());
        m.put("submissionsPerDay", o.submissionsPerDay());
        return m;
    }

    static Map<String, Object> funnel(Funnel f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("started", f.started());
        m.put("steps", f.steps().stream().map(s -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("question", s.question().value());
            x.put("title", s.title());
            x.put("answered", s.answered());
            x.put("percentOfStarted", s.percentOfStarted());
            return x;
        }).toList());
        m.put("abandonedAfter", f.abandonedAfter());
        return m;
    }

    static Map<String, Object> crosstab(Crosstab c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", c.row().value());
        m.put("column", c.column().value());
        m.put("measure", c.measure());
        m.put("rowKeys", c.rowKeys());
        m.put("columnKeys", c.columnKeys());
        m.put("cells", c.cells());
        return m;
    }

    static List<Map<String, Object>> keywords(List<Keyword> ks) {
        return ks.stream().map(k -> Map.<String, Object>of("word", k.word(), "count", k.count())).toList();
    }

    static Map<String, Object> quizItems(QuizItemAnalysis q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attempts", q.attempts());
        m.put("meanPercent", q.meanPercent());
        m.put("stdDevPercent", q.stdDevPercent());
        m.put("histogram", q.histogram());
        m.put("items", q.items().stream().map(i -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("question", i.question().value());
            x.put("title", i.title());
            x.put("correctRate", i.correctRate());
            x.put("averageEarned", i.averageEarned());
            x.put("maxPoints", i.maxPoints());
            x.put("discrimination", i.discrimination());
            return x;
        }).toList());
        return m;
    }

    static List<Map<String, Object>> leaderboard(List<LeaderboardEntry> es) {
        return es.stream().map(e -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("rank", e.rank());
            x.put("respondent", e.respondent());
            x.put("percent", e.percent());
            x.put("earned", e.earned());
            x.put("durationSeconds", e.durationSeconds());
            return x;
        }).toList();
    }

    static List<Map<String, Object>> channels(List<ChannelStat> cs) {
        return cs.stream().map(c -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("value", c.value());
            x.put("started", c.started());
            x.put("submitted", c.submitted());
            x.put("completionRate", c.completionRate());
            return x;
        }).toList();
    }

    static List<Map<String, Object>> indices(List<IndexResult> rs) {
        return rs.stream().map(r -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", r.name());
            x.put("n", r.n());
            x.put("mean", r.mean());
            x.put("min", r.min());
            x.put("max", r.max());
            x.put("histogram", r.histogram());
            x.put("byGroup", r.byGroup());
            return x;
        }).toList();
    }

    static Map<String, Object> ranked(RankedChoiceResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("question", r.question().value());
        m.put("ballots", r.ballots());
        m.put("winner", r.winner());
        m.put("condorcetWinner", r.condorcetWinner());
        m.put("firstChoice", r.firstChoice());
        m.put("borda", r.borda());
        m.put("rounds", r.rounds().stream().map(rd -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("round", rd.number());
            x.put("tallies", rd.tallies());
            x.put("eliminated", rd.eliminated());
            x.put("exhausted", rd.exhausted());
            return x;
        }).toList());
        return m;
    }

    static Map<String, Object> estimates(Estimates e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("n", e.n());
        m.put("weighted", e.weighted());
        m.put("effectiveN", e.effectiveN());
        m.put("designEffect", e.designEffect());
        m.put("excluded", e.excluded());
        m.put("questions", e.questions().stream().map(q -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("question", q.question().value());
            x.put("title", q.title());
            x.put("base", q.base());
            x.put("options", q.options().stream().map(o -> {
                Map<String, Object> y = new LinkedHashMap<>();
                y.put("option", o.option());
                y.put("percent", o.percent());
                y.put("marginOfError", o.marginOfError());
                return y;
            }).toList());
            return x;
        }).toList());
        return m;
    }
}
