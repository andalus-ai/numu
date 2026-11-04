package tech.kayys.numou.talent.domain.profile;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable professional profile sections. These are presentation/evidence
 * references, not employment, HR, or credential-issuing system-of-records.
 */
public final class ProfileContent {
    private ProfileContent() {}

    public enum SkillLevel { BEGINNER, INTERMEDIATE, ADVANCED, EXPERT }
    public enum Verification { SELF_REPORTED, ISSUER_VERIFIED, PLATFORM_VERIFIED }

    public record Skill(UUID id, String name, String category, SkillLevel level, Verification verification) {
        public Skill {
            id = required(id, "skill id");
            name = text(name, "skill name", 120);
            category = optional(category, "skill category", 80);
            level = Objects.requireNonNull(level, "skill level");
            verification = Objects.requireNonNull(verification, "skill verification");
        }
    }

    public record Experience(
            UUID id, String employerName, String roleTitle, String locationLabel,
            LocalDate startDate, LocalDate endDate, boolean current, String description
    ) {
        public Experience {
            id = required(id, "experience id");
            employerName = text(employerName, "employer name", 160);
            roleTitle = text(roleTitle, "role title", 160);
            locationLabel = optional(locationLabel, "experience location", 160);
            startDate = Objects.requireNonNull(startDate, "experience start date");
            if (current && endDate != null) throw new IllegalArgumentException("Current experience cannot have an end date");
            if (!current && endDate == null) throw new IllegalArgumentException("Past experience must have an end date");
            if (endDate != null && endDate.isBefore(startDate)) throw new IllegalArgumentException("Experience end date precedes start date");
            description = optional(description, "experience description", 4_000);
        }
    }

    public record Education(
            UUID id, String institution, String qualification, String fieldOfStudy,
            LocalDate startDate, LocalDate endDate, String description
    ) {
        public Education {
            id = required(id, "education id");
            institution = text(institution, "institution", 180);
            qualification = text(qualification, "qualification", 160);
            fieldOfStudy = optional(fieldOfStudy, "field of study", 160);
            if (startDate != null && endDate != null && endDate.isBefore(startDate))
                throw new IllegalArgumentException("Education end date precedes start date");
            description = optional(description, "education description", 2_000);
        }
    }

    public record Credential(
            UUID id, String name, String issuer, LocalDate issuedOn, LocalDate expiresOn,
            UUID documentId, Verification verification
    ) {
        public Credential {
            id = required(id, "credential id");
            name = text(name, "credential name", 180);
            issuer = text(issuer, "credential issuer", 180);
            if (issuedOn != null && expiresOn != null && expiresOn.isBefore(issuedOn))
                throw new IllegalArgumentException("Credential expiry precedes issue date");
            verification = Objects.requireNonNull(verification, "credential verification");
        }
    }

    public record PortfolioItem(
            UUID id, String title, String description, URI externalUri, UUID documentId
    ) {
        public PortfolioItem {
            id = required(id, "portfolio item id");
            title = text(title, "portfolio title", 180);
            description = optional(description, "portfolio description", 2_000);
            if (externalUri == null && documentId == null)
                throw new IllegalArgumentException("Portfolio item must reference a URL or a Document");
            if (externalUri != null && !"https".equalsIgnoreCase(externalUri.getScheme()))
                throw new IllegalArgumentException("Portfolio URL must use HTTPS");
            if (externalUri != null && (externalUri.getHost() == null || externalUri.getUserInfo() != null))
                throw new IllegalArgumentException("Portfolio URL must have a host and no embedded credentials");
        }
    }

    private static UUID required(UUID value, String field) {
        return Objects.requireNonNull(value, field + " must not be null");
    }

    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max)
            throw new IllegalArgumentException(field + " must contain 1.." + max + " characters");
        return normalized;
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }
}
