package tech.kayys.syirkah.survey.domain.quiz;

import tech.kayys.syirkah.survey.domain.DomainException;
import tech.kayys.syirkah.survey.domain.ErrorCode;

import java.time.Duration;

/**
 * @param timeLimit   Duration.ZERO = untimed
 * @param maxAttempts 0 = unlimited attempts per respondent
 */
public record QuizSettings(double passingPercent, Duration timeLimit, int maxAttempts, boolean shuffleQuestions,
                           ResultsVisibility resultsVisibility, boolean showCorrectAnswers) {
    public QuizSettings {
        if (passingPercent < 0 || passingPercent > 100)
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "passingPercent must be within 0..100");
        if (timeLimit == null) timeLimit = Duration.ZERO;
        if (timeLimit.isNegative()) throw new DomainException(ErrorCode.VALIDATION_FAILED, "timeLimit must not be negative");
        if (maxAttempts < 0) throw new DomainException(ErrorCode.VALIDATION_FAILED, "maxAttempts must not be negative");
        if (resultsVisibility == null) resultsVisibility = ResultsVisibility.IMMEDIATE;
    }

    public static QuizSettings defaults() {
        return new QuizSettings(60, Duration.ZERO, 1, false, ResultsVisibility.IMMEDIATE, true);
    }

    public boolean timed() { return !timeLimit.isZero(); }
}
