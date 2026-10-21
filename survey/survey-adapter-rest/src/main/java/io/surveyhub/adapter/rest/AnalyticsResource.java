package io.surveyhub.adapter.rest;

import io.surveyhub.application.port.in.AnalyticsUseCase;
import io.surveyhub.domain.analytics.AnalyticsFilter;
import io.surveyhub.domain.id.OptionId;
import io.surveyhub.domain.id.QuestionId;
import io.surveyhub.domain.id.SurveyId;
import io.surveyhub.presentation.CsvExport;
import io.surveyhub.presentation.ReportAssembler;
import io.surveyhub.presentation.ReportPage;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Analytics for the tenant's own surveys. Common optional filters on every endpoint:
 * from / to (ISO-8601 instants) and segmentQuestion + segmentOption ("only respondents who picked this option").
 */
@Path("/api/v1/surveys/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class AnalyticsResource {
    private final AnalyticsUseCase analytics;
    private final RequestTenant tenant;
    private final Clock clock;

    @Inject
    public AnalyticsResource(AnalyticsUseCase analytics, RequestTenant tenant, Clock clock) {
        this.analytics = analytics;
        this.tenant = tenant;
        this.clock = clock;
    }

    static AnalyticsFilter filter(String from, String to, String segmentQuestion, String segmentOption) {
        return new AnalyticsFilter(blank(from) ? null : Instant.parse(from), blank(to) ? null : Instant.parse(to),
                blank(segmentQuestion) || blank(segmentOption) ? null : new QuestionId(segmentQuestion),
                blank(segmentQuestion) || blank(segmentOption) ? null : new OptionId(segmentOption));
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    @GET @Path("results")
    public Map<String, Object> results(@PathParam("id") String id, @QueryParam("from") String from, @QueryParam("to") String to,
                                       @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.results(analytics.summary(tenant.get(), new SurveyId(id), filter(from, to, sq, so)));
    }

    @GET @Path("analytics/overview")
    public Map<String, Object> overview(@PathParam("id") String id, @QueryParam("from") String from, @QueryParam("to") String to,
                                        @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.overview(analytics.overview(tenant.get(), new SurveyId(id), filter(from, to, sq, so)));
    }

    @GET @Path("analytics/funnel")
    public Map<String, Object> funnel(@PathParam("id") String id, @QueryParam("from") String from, @QueryParam("to") String to,
                                      @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.funnel(analytics.funnel(tenant.get(), new SurveyId(id), filter(from, to, sq, so)));
    }

    @GET @Path("analytics/crosstab")
    public Map<String, Object> crosstab(@PathParam("id") String id, @QueryParam("row") String row, @QueryParam("column") String column,
                                        @QueryParam("from") String from, @QueryParam("to") String to,
                                        @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.crosstab(analytics.crosstab(tenant.get(), new SurveyId(id), new QuestionId(row), new QuestionId(column),
                filter(from, to, sq, so)));
    }

    @GET @Path("analytics/keywords")
    public List<Map<String, Object>> keywords(@PathParam("id") String id, @QueryParam("question") String question,
                                              @QueryParam("limit") @DefaultValue("20") int limit,
                                              @QueryParam("from") String from, @QueryParam("to") String to,
                                              @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.keywords(analytics.keywords(tenant.get(), new SurveyId(id), new QuestionId(question), limit, filter(from, to, sq, so)));
    }

    @GET @Path("analytics/quiz/items")
    public Map<String, Object> quizItems(@PathParam("id") String id, @QueryParam("from") String from, @QueryParam("to") String to,
                                         @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return analytics.quizItems(tenant.get(), new SurveyId(id), filter(from, to, sq, so))
                .map(AnalyticsJson::quizItems).orElse(Map.of());
    }

    @GET @Path("analytics/quiz/leaderboard")
    public List<Map<String, Object>> leaderboard(@PathParam("id") String id, @QueryParam("limit") @DefaultValue("10") int limit,
                                                 @QueryParam("from") String from, @QueryParam("to") String to,
                                                 @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.leaderboard(analytics.leaderboard(tenant.get(), new SurveyId(id), limit, filter(from, to, sq, so)));
    }

    /** Campaign performance per value of a link tag (utm_source, utm_campaign, ref, ...). */
    @GET @Path("analytics/channels")
    public List<Map<String, Object>> channels(@PathParam("id") String id, @QueryParam("key") @DefaultValue("utm_source") String key,
                                              @QueryParam("from") String from, @QueryParam("to") String to,
                                              @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.channels(analytics.channels(tenant.get(), new SurveyId(id), key, filter(from, to, sq, so)));
    }

    /** Composite indices; optional {@code by} = categorical question id for a mean-per-group breakdown. */
    @GET @Path("analytics/indices")
    public List<Map<String, Object>> indices(@PathParam("id") String id, @QueryParam("by") String by,
                                             @QueryParam("from") String from, @QueryParam("to") String to,
                                             @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.indices(analytics.indices(tenant.get(), new SurveyId(id), blank(by) ? null : new QuestionId(by), filter(from, to, sq, so)));
    }

    /** Instant-runoff rounds, Borda points and Condorcet winner for a ranking question. */
    @GET @Path("analytics/ranked")
    public Map<String, Object> ranked(@PathParam("id") String id, @QueryParam("question") String question,
                                      @QueryParam("from") String from, @QueryParam("to") String to,
                                      @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.ranked(analytics.rankedChoice(tenant.get(), new SurveyId(id), new QuestionId(question), filter(from, to, sq, so)));
    }

    /** Unweighted percentages with 95% margins of error. */
    @GET @Path("analytics/estimates")
    public Map<String, Object> estimates(@PathParam("id") String id, @QueryParam("from") String from, @QueryParam("to") String to,
                                         @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        return AnalyticsJson.estimates(analytics.estimates(tenant.get(), new SurveyId(id), null, filter(from, to, sq, so)));
    }

    /** Weighted estimates. Body: {"question":"age","targets":{"a18":0.2,"a30":0.3,"a45":0.3,"a60":0.2}} (shares of the population). */
    @POST @Path("analytics/estimates") @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> weightedEstimates(@PathParam("id") String id, com.fasterxml.jackson.databind.JsonNode body,
                                                 @QueryParam("from") String from, @QueryParam("to") String to,
                                                 @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        java.util.Map<OptionId, Double> targets = new java.util.LinkedHashMap<>();
        body.path("targets").fields().forEachRemaining(e -> targets.put(new OptionId(e.getKey()), e.getValue().asDouble()));
        var weighting = new io.surveyhub.domain.analytics.Weighting(new QuestionId(body.path("question").asText(null)), targets);
        return AnalyticsJson.estimates(analytics.estimates(tenant.get(), new SurveyId(id), weighting, filter(from, to, sq, so)));
    }

    /** Printable HTML report (browser "Print to PDF" gives a PDF). */
    @GET @Path("report") @Produces(MediaType.TEXT_HTML)
    public Response report(@PathParam("id") String id, @QueryParam("from") String from, @QueryParam("to") String to,
                           @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        var data = ReportAssembler.assemble(analytics, tenant.get(), new SurveyId(id), filter(from, to, sq, so), clock);
        return Response.ok(ReportPage.render(data))
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'")
                .header("Cache-Control", "no-store").build();
    }

    /** Raw responses, one row per response / one column per question. */
    @GET @Path("export.csv") @Produces("text/csv")
    public Response csv(@PathParam("id") String id, @QueryParam("from") String from, @QueryParam("to") String to,
                        @QueryParam("segmentQuestion") String sq, @QueryParam("segmentOption") String so) {
        var data = analytics.dataset(tenant.get(), new SurveyId(id), filter(from, to, sq, so));
        String name = "survey-" + data.survey().id().value().replaceAll("[^A-Za-z0-9_-]", "") + ".csv";
        return Response.ok(CsvExport.render(data)).header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .header("Cache-Control", "no-store").build();
    }
}
