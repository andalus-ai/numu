package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.crm.domain.model.ConversionReport;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * View of a conversion report.
 */
public record ConversionReportView(
        String reportId,
        String reportName,
        String period,
        String periodStart,
        String periodEnd,
        int totalLeads,
        int convertedLeads,
        int lostLeads,
        int qualifiedLeads,
        double conversionRate,
        List<SourceView> bySource,
        List<IndustryView> byIndustry,
        String generatedBy,
        String generatedAt
) {

    public static ConversionReportView fromDomain(ConversionReport report) {
        return new ConversionReportView(
            report.getId().toString(),
            report.getReportName(),
            report.getPeriod(),
            report.getPeriodStart().toString(),
            report.getPeriodEnd().toString(),
            report.getTotalLeads(),
            report.getConvertedLeads(),
            report.getLostLeads(),
            report.getQualifiedLeads(),
            report.getConversionRate(),
            report.getBySource().stream()
                .map(SourceView::fromDomain)
                .collect(Collectors.toList()),
            report.getByIndustry().stream()
                .map(IndustryView::fromDomain)
                .collect(Collectors.toList()),
            report.getGeneratedBy(),
            report.getGeneratedAt().toString()
        );
    }

    public record SourceView(
            String source,
            int total,
            int converted,
            double rate
    ) {
        public static SourceView fromDomain(ConversionReport.ConversionSource source) {
            return new SourceView(
                source.getSource(),
                source.getTotal(),
                source.getConverted(),
                source.getRate()
            );
        }
    }

    public record IndustryView(
            String industry,
            int total,
            int converted,
            double rate
    ) {
        public static IndustryView fromDomain(ConversionReport.ConversionIndustry industry) {
            return new IndustryView(
                industry.getIndustry(),
                industry.getTotal(),
                industry.getConverted(),
                industry.getRate()
            );
        }
    }
}