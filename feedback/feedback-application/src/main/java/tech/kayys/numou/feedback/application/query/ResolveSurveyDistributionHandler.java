package tech.kayys.numou.feedback.application.query;

import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.domain.survey.SurveyDistribution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Resolves an opaque public invitation token without exposing token hashes to callers. */
public final class ResolveSurveyDistributionHandler {
    private final SurveyDistributionRepository repository;

    public ResolveSurveyDistributionHandler(SurveyDistributionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    public Optional<SurveyDistribution> handle(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) return Optional.empty();
        return repository.findActiveByTokenHash(sha256(rawToken));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
