package tech.kayys.numou.feedback.domain.survey;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackSurveyTemplateTest {
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final String DEFINITION = "{\"kind\":\"SURVEY\",\"questions\":[]}";

    @Test
    void draftComputesStableChecksumAndCanBePublished() {
        var draft = FeedbackSurveyTemplate.draft(TenantId.generate(), "csat", 1,
                "Customer satisfaction", "Standard CSAT", DEFINITION, NOW);

        var published = draft.publish(NOW.plusSeconds(1));

        assertEquals(64, draft.checksum().length());
        assertEquals(TemplateStatus.DRAFT, draft.status());
        assertEquals(TemplateStatus.PUBLISHED, published.status());
        assertEquals(draft.checksum(), published.checksum());
        assertThrows(IllegalStateException.class, () -> published.publish(NOW.plusSeconds(2)));
    }

    @Test
    void rejectsChecksumMismatchAndInvalidPublishedTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> new FeedbackSurveyTemplate(
                TenantId.generate(), "csat", 1, "CSAT", "", DEFINITION, "0".repeat(64),
                TemplateStatus.DRAFT, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new FeedbackSurveyTemplate(
                TenantId.generate(), "csat", 1, "CSAT", "", DEFINITION, null,
                TemplateStatus.PUBLISHED, NOW, null));
    }

    @Test
    void requiresPositiveVersionAndDefinition() {
        assertThrows(IllegalArgumentException.class, () -> FeedbackSurveyTemplate.draft(
                TenantId.generate(), "csat", 0, "CSAT", "", DEFINITION, NOW));
        assertThrows(IllegalArgumentException.class, () -> FeedbackSurveyTemplate.draft(
                TenantId.generate(), "csat", 1, "CSAT", "", " ", NOW));
    }
}
