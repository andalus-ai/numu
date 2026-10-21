package io.surveyhub.presentation;

import io.surveyhub.application.TenantContext;
import io.surveyhub.application.port.in.AnalyticsUseCase;
import io.surveyhub.domain.analytics.AnalyticsFilter;
import io.surveyhub.domain.analytics.Keyword;
import io.surveyhub.domain.analytics.RankedChoiceResult;
import io.surveyhub.domain.id.QuestionId;
import io.surveyhub.domain.id.SurveyId;
import io.surveyhub.domain.question.Question;
import io.surveyhub.domain.question.QuestionType;
import io.surveyhub.domain.survey.Survey;
import io.surveyhub.domain.survey.SurveyKind;

import java.time.Clock;
import java.util.*;

/** Gathers everything a report needs through the analytics use case, then renders it. */
public final class ReportAssembler {
    private ReportAssembler() {}

    public static ReportData assemble(AnalyticsUseCase analytics, TenantContext ctx, SurveyId id, AnalyticsFilter filter, Clock clock) {
        Survey survey = analytics.survey(ctx, id);
        Map<QuestionId, List<Keyword>> keywords = new LinkedHashMap<>();
        for (Question q : survey.questions()) {
            if (q.type() instanceof QuestionType.ShortText || q.type() instanceof QuestionType.LongText)
                keywords.put(q.id(), analytics.keywords(ctx, id, q.id(), 8, filter));
        }
        boolean quiz = survey.kind() == SurveyKind.QUIZ;
        var channels = analytics.channels(ctx, id, "utm_source", filter);
        boolean tagged = channels.stream().anyMatch(c -> !c.value().equals("(none)"));
        Map<QuestionId, RankedChoiceResult> ranked = new LinkedHashMap<>();
        for (Question q : survey.questions())
            if (q.type() instanceof QuestionType.Ranking) ranked.put(q.id(), analytics.rankedChoice(ctx, id, q.id(), filter));
        return new ReportData(survey, analytics.summary(ctx, id, filter), analytics.overview(ctx, id, filter),
                analytics.funnel(ctx, id, filter), keywords,
                quiz ? analytics.quizItems(ctx, id, filter) : Optional.empty(),
                quiz ? analytics.leaderboard(ctx, id, 10, filter) : List.of(), clock.instant().toString(),
                "utm_source", tagged ? channels : List.of(),
                survey.indices().isEmpty() ? List.of() : analytics.indices(ctx, id, null, filter),
                ranked, analytics.estimates(ctx, id, null, filter));
    }
}
