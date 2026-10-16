package tech.kayys.numou.crm.application.port;

import tech.kayys.numou.crm.domain.model.Lead;

import java.util.concurrent.CompletionStage;

/**
 * Port for lead scoring.
 */
public interface LeadScoringPort {

    /**
     * Calculates a score for a lead based on its attributes.
     */
    CompletionStage<Double> calculateScore(Lead lead);

    /**
     * Gets the lead scoring rules.
     */
    CompletionStage<ScoringRules> getScoringRules();

    record ScoringRules(
        int emailScore,
        int phoneScore,
        int companyScore,
        int jobTitleScore,
        int industryScore
    ) {}
}