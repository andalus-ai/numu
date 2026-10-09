package tech.kayys.syirkah.survey.domain.survey;

import java.time.Instant;

/**
 * @param maxResponses 0 = unlimited
 * @param opensAt      null = open as soon as published
 * @param closesAt     null = open until closed manually
 */
public record SurveySettings(boolean anonymous, boolean allowMultipleResponses, int maxResponses,
                             Instant opensAt, Instant closesAt, String thankYouMessage) {
    public SurveySettings {
        thankYouMessage = thankYouMessage == null ? "" : thankYouMessage;
        if (maxResponses < 0) maxResponses = 0;
    }

    public static SurveySettings defaults() { return new SurveySettings(false, false, 0, null, null, "Thank you!"); }
}
