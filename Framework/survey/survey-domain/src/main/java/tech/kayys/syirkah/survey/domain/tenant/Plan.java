package tech.kayys.syirkah.survey.domain.tenant;

import tech.kayys.syirkah.survey.domain.DomainException;
import tech.kayys.syirkah.survey.domain.ErrorCode;

public record Plan(String name, int maxPublishedSurveys, int maxQuestionsPerSurvey, int maxResponsesPerSurvey,
                   boolean quizEnabled) {
    public static final Plan FREE = new Plan("free", 3, 20, 100, false);
    public static final Plan PRO = new Plan("pro", 50, 200, 10_000, true);
    public static final Plan ENTERPRISE = new Plan("enterprise", Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true);

    public static Plan byName(String name) {
        return switch (name == null ? "" : name.toLowerCase()) {
            case "free" -> FREE;
            case "pro" -> PRO;
            case "enterprise" -> ENTERPRISE;
            default -> throw new DomainException(ErrorCode.VALIDATION_FAILED, "Unknown plan: " + name);
        };
    }
}
