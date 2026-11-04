package tech.kayys.numou.talent.domain.profile;

import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyRef;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.domain.profile.event.TalentProfileCreated;
import tech.kayys.numou.talent.domain.profile.event.TalentProfilePublished;
import tech.kayys.numou.talent.domain.profile.event.TalentProfileVisibilityChanged;

import java.time.Instant;
import java.util.Objects;

/**
 * Tenant-scoped professional profile owned by a canonical PERSON Party.
 *
 * <p>This aggregate intentionally contains professional presentation data only.
 * Authentication, contact points, identity documents, employment records,
 * workforce assignments and candidate applications remain owned by their
 * respective capabilities.
 */
public final class TalentProfile extends AbstractAggregateRoot<TalentProfileId> {

    public static final int HEADLINE_MAX_LENGTH = 160;
    public static final int SUMMARY_MAX_LENGTH = 5_000;
    public static final int LOCATION_MAX_LENGTH = 160;

    private final TenantId tenantId;
    private final PartyRef subject;
    private String headline;
    private String summary;
    private String locationLabel;
    private ProfileVisibility visibility;
    private TalentProfileStatus status;

    private TalentProfile(
            TalentProfileId id,
            TenantId tenantId,
            PartyRef subject,
            String headline,
            String summary,
            String locationLabel
    ) {
        super(Objects.requireNonNull(id, "Talent profile ID must not be null"));
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        this.subject = requirePerson(subject);
        this.headline = requireText(headline, "headline", HEADLINE_MAX_LENGTH);
        this.summary = optionalText(summary, "summary", SUMMARY_MAX_LENGTH);
        this.locationLabel = optionalText(locationLabel, "locationLabel", LOCATION_MAX_LENGTH);
        this.visibility = ProfileVisibility.PRIVATE;
        this.status = TalentProfileStatus.DRAFT;
    }

    public static TalentProfile create(
            TalentProfileId id,
            TenantId tenantId,
            PartyRef subject,
            String headline,
            String summary,
            String locationLabel
    ) {
        var profile = new TalentProfile(id, tenantId, subject, headline, summary, locationLabel);
        profile.raise(new TalentProfileCreated(id, tenantId, subject));
        return profile;
    }

    /** Replaces the professional presentation without changing identity or publication state. */
    public void updatePresentation(String headline, String summary, String locationLabel) {
        ensureEditable();
        this.headline = requireText(headline, "headline", HEADLINE_MAX_LENGTH);
        this.summary = optionalText(summary, "summary", SUMMARY_MAX_LENGTH);
        this.locationLabel = optionalText(locationLabel, "locationLabel", LOCATION_MAX_LENGTH);
        touch();
    }

    /** Changes discoverability independently from publication and authorization. */
    public void changeVisibility(ProfileVisibility visibility) {
        Objects.requireNonNull(visibility, "Visibility must not be null");
        if (status == TalentProfileStatus.ARCHIVED) {
            throw new IllegalStateException("Archived talent profile cannot change visibility");
        }
        if (this.visibility == visibility) {
            return;
        }
        var previous = this.visibility;
        this.visibility = visibility;
        touch();
        raise(new TalentProfileVisibilityChanged(id(), tenantId, previous, visibility));
    }

    public void publish() {
        if (status == TalentProfileStatus.ARCHIVED) {
            throw new IllegalStateException("Archived talent profile cannot be published");
        }
        if (status == TalentProfileStatus.SUSPENDED) {
            throw new IllegalStateException("Suspended talent profile cannot be published");
        }
        if (status == TalentProfileStatus.PUBLISHED) {
            return;
        }
        status = TalentProfileStatus.PUBLISHED;
        touch();
        raise(new TalentProfilePublished(id(), tenantId, subject));
    }

    public void suspend() {
        if (status == TalentProfileStatus.ARCHIVED) {
            throw new IllegalStateException("Archived talent profile cannot be suspended");
        }
        if (status == TalentProfileStatus.SUSPENDED) {
            return;
        }
        status = TalentProfileStatus.SUSPENDED;
        touch();
    }

    public void archive() {
        if (status == TalentProfileStatus.ARCHIVED) {
            return;
        }
        status = TalentProfileStatus.ARCHIVED;
        visibility = ProfileVisibility.PRIVATE;
        touch();
    }

    private void ensureEditable() {
        if (status == TalentProfileStatus.ARCHIVED) {
            throw new IllegalStateException("Archived talent profile cannot be edited");
        }
        if (status == TalentProfileStatus.SUSPENDED) {
            throw new IllegalStateException("Suspended talent profile cannot be edited");
        }
    }

    private void touch() {
        updatedAt = Instant.now();
        incrementVersion();
    }

    private static PartyRef requirePerson(PartyRef subject) {
        Objects.requireNonNull(subject, "Profile subject must not be null");
        if (subject.kind() != PartyKind.PERSON) {
            throw new IllegalArgumentException("Talent profile subject must reference a PERSON Party");
        }
        return subject;
    }

    private static String requireText(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " must not be null");
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    public TenantId tenantId() { return tenantId; }
    public PartyRef subject() { return subject; }
    public String headline() { return headline; }
    public String summary() { return summary; }
    public String locationLabel() { return locationLabel; }
    public ProfileVisibility visibility() { return visibility; }
    public TalentProfileStatus status() { return status; }
}
