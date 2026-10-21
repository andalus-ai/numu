package io.surveyhub.presentation;

import io.surveyhub.domain.analytics.*;
import io.surveyhub.domain.id.QuestionId;
import io.surveyhub.domain.survey.Survey;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a printable report shows; assembled by the caller from the analytics use cases.
 *
 * @param channels   campaign performance by {@code channelKey} (empty when no tags were captured)
 * @param indices    composite index results (empty when the survey defines none)
 * @param ranked     instant-runoff / Borda / Condorcet per ranking question
 * @param estimates  percentages with margins of error (may be null)
 */
public record ReportData(Survey survey, SurveyResults results, Overview overview, Funnel funnel,
                         Map<QuestionId, List<Keyword>> keywords, Optional<QuizItemAnalysis> quiz,
                         List<LeaderboardEntry> leaderboard, String generatedAt,
                         String channelKey, List<ChannelStat> channels, List<IndexResult> indices,
                         Map<QuestionId, RankedChoiceResult> ranked, Estimates estimates) {

    public ReportData(Survey survey, SurveyResults results, Overview overview, Funnel funnel,
                      Map<QuestionId, List<Keyword>> keywords, Optional<QuizItemAnalysis> quiz,
                      List<LeaderboardEntry> leaderboard, String generatedAt) {
        this(survey, results, overview, funnel, keywords, quiz, leaderboard, generatedAt,
                "utm_source", List.of(), List.of(), Map.of(), null);
    }
}
