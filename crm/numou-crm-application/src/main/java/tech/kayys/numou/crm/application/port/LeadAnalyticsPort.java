package tech.kayys.numou.crm.application.port;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Port for lead analytics data.
 */
public interface LeadAnalyticsPort {

    /**
     * Gets conversion data for a date range.
     */
    CompletionStage<ConversionData> getConversionData(Instant from, Instant to);

    /**
     * Gets lead source analytics.
     */
    CompletionStage<List<SourceData>> getLeadSourceAnalytics(Instant from, Instant to);

    /**
     * Conversion data record.
     */
    record ConversionData(
        int totalLeads,
        int convertedLeads,
        int lostLeads,
        int qualifiedLeads,
        List<SourceData> bySource,
        List<IndustryData> byIndustry
    ) {}

    /**
     * Source data record.
     */
    record SourceData(
        String source,
        int total,
        int converted
    ) {}

    /**
     * Industry data record.
     */
    record IndustryData(
        String industry,
        int total,
        int converted
    ) {}
}