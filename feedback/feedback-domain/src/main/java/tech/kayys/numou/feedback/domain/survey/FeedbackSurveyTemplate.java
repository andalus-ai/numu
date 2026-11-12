package tech.kayys.numou.feedback.domain.survey;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable, tenant-owned snapshot of a Survey template version.
 * The serialized Survey definition is opaque here so Feedback does not duplicate
 * Survey's question/logic model or introduce a reverse dependency.
 */
public record FeedbackSurveyTemplate(
        TenantId tenantId,
        String code,
        int version,
        String title,
        String description,
        String surveyDefinition,
        String checksum,
        TemplateStatus status,
        Instant createdAt,
        Instant publishedAt) {

    public FeedbackSurveyTemplate {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        code = required(code, "code", 100);
        title = required(title, "title", 200);
        description = description == null ? "" : description.trim();
        if (description.length() > 2000) throw new IllegalArgumentException("description exceeds 2000 characters");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        surveyDefinition = required(surveyDefinition, "surveyDefinition", 1_000_000);
        String expected = sha256(surveyDefinition);
        if (checksum == null || checksum.isBlank()) checksum = expected;
        if (!expected.equalsIgnoreCase(checksum)) throw new IllegalArgumentException("template checksum does not match definition");
        checksum = expected;
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        if (status == TemplateStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("publishedAt is required for published templates");
        if (status != TemplateStatus.PUBLISHED && publishedAt != null)
            throw new IllegalArgumentException("publishedAt is only valid for published templates");
    }

    public static FeedbackSurveyTemplate draft(TenantId tenantId, String code, int version,
            String title, String description, String surveyDefinition, Instant now) {
        return new FeedbackSurveyTemplate(tenantId, code, version, title, description,
                surveyDefinition, null, TemplateStatus.DRAFT, now, null);
    }

    public FeedbackSurveyTemplate publish(Instant now) {
        if (status != TemplateStatus.DRAFT) throw new IllegalStateException("only draft templates can be published");
        return new FeedbackSurveyTemplate(tenantId, code, version, title, description,
                surveyDefinition, checksum, TemplateStatus.PUBLISHED, createdAt,
                Objects.requireNonNull(now, "now cannot be null"));
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(field + " must contain 1.." + max + " characters");
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
