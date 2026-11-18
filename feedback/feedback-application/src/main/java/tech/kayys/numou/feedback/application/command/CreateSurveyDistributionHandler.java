package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.domain.survey.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Creates high-entropy invitation credentials without persisting bearer tokens. */
public final class CreateSurveyDistributionHandler {
    private final SurveyDistributionRepository repository;
    private final SecureRandom random;

    public CreateSurveyDistributionHandler(SurveyDistributionRepository repository) {
        this(repository, new SecureRandom());
    }

    CreateSurveyDistributionHandler(SurveyDistributionRepository repository, SecureRandom random) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    public CreatedSurveyDistribution handle(CreateSurveyDistributionCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String hash = sha256(token);
        SurveyDistribution distribution = new SurveyDistribution(UUID.randomUUID(),
                command.tenantId(), command.surveyId(), command.channel(), hash,
                command.expiresAt(), command.maxResponses(), DistributionStatus.ACTIVE,
                command.createdAt());
        repository.save(distribution);
        return new CreatedSurveyDistribution(distribution, token);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record CreatedSurveyDistribution(SurveyDistribution distribution, String rawToken) { }
}
