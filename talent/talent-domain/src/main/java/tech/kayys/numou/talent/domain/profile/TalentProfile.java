package tech.kayys.numou.talent.domain.profile;

import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyRef;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.repository.TenantAware;
import tech.kayys.numou.talent.domain.profile.event.TalentProfileCreated;
import tech.kayys.numou.talent.domain.profile.event.TalentProfileContentChanged;
import tech.kayys.numou.talent.domain.profile.event.TalentProfilePublished;
import tech.kayys.numou.talent.domain.profile.event.TalentProfilePresentationChanged;
import tech.kayys.numou.talent.domain.profile.event.TalentProfileVisibilityChanged;

import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped professional profile owned by a canonical PERSON Party.
 *
 * <p>This aggregate intentionally contains professional presentation data only.
 * Authentication, contact points, identity documents, employment records,
 * workforce assignments and candidate applications remain owned by their
 * respective capabilities.
 */
public final class TalentProfile extends AbstractAggregateRoot<TalentProfileId> implements TenantAware {

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
    private List<ProfileContent.Skill> skills = List.of();
    private List<ProfileContent.Experience> experiences = List.of();
    private List<ProfileContent.Education> education = List.of();
    private List<ProfileContent.Credential> credentials = List.of();
    private List<ProfileContent.PortfolioItem> portfolio = List.of();

    public static final int MAX_PROFILE_ITEMS = 100;

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

    /**
     * Reconstitutes a previously persisted aggregate without raising creation
     * events or applying a new lifecycle transition. Persistence adapters must
     * supply the complete snapshot and the exact optimistic-lock version.
     */
    public static TalentProfile rehydrate(
            TalentProfileId id,
            TenantId tenantId,
            PartyRef subject,
            String headline,
            String summary,
            String locationLabel,
            ProfileVisibility visibility,
            TalentProfileStatus status,
            List<ProfileContent.Skill> skills,
            List<ProfileContent.Experience> experiences,
            List<ProfileContent.Education> education,
            List<ProfileContent.Credential> credentials,
            List<ProfileContent.PortfolioItem> portfolio,
            Instant createdAt,
            Instant updatedAt,
            int version
    ) {
        Objects.requireNonNull(visibility, "visibility must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
        if (status == TalentProfileStatus.ARCHIVED && visibility != ProfileVisibility.PRIVATE) {
            throw new IllegalArgumentException("Archived profile must have private visibility");
        }

        TalentProfile profile = new TalentProfile(id, tenantId, subject, headline, summary, locationLabel);
        profile.visibility = visibility;
        profile.status = status;
        profile.skills = validatedItems(skills, "skills");
        profile.experiences = validatedItems(experiences, "experiences");
        profile.education = validatedItems(education, "education");
        profile.credentials = validatedItems(credentials, "credentials");
        profile.portfolio = validatedItems(portfolio, "portfolio");
        profile.setCreatedAt(createdAt);
        profile.setUpdatedAt(updatedAt);
        profile.setVersion(version);
        return profile;
    }

    /** Replaces the professional presentation without changing identity or publication state. */
    public void updatePresentation(String headline, String summary, String locationLabel) {
        ensureEditable();
        var normalizedHeadline = requireText(headline, "headline", HEADLINE_MAX_LENGTH);
        var normalizedSummary = optionalText(summary, "summary", SUMMARY_MAX_LENGTH);
        var normalizedLocation = optionalText(locationLabel, "locationLabel", LOCATION_MAX_LENGTH);
        this.headline = normalizedHeadline;
        this.summary = normalizedSummary;
        this.locationLabel = normalizedLocation;
        touch();
        raise(new TalentProfilePresentationChanged(id(), tenantId));
    }

    /** Changes discoverability independently from publication and authorization. */
    public void changeVisibility(ProfileVisibility visibility) {
        Objects.requireNonNull(visibility, "Visibility must not be null");
        ensureEditable();
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
        raise(new tech.kayys.numou.talent.domain.profile.event.TalentProfileSuspended(id(), tenantId));
    }

    public void archive() {
        if (status == TalentProfileStatus.ARCHIVED) {
            return;
        }
        status = TalentProfileStatus.ARCHIVED;
        visibility = ProfileVisibility.PRIVATE;
        touch();
        raise(new tech.kayys.numou.talent.domain.profile.event.TalentProfileArchived(id(), tenantId));
    }


    public void replaceSkills(List<ProfileContent.Skill> items) {
        ensureEditable();
        skills = validatedItems(items, "skills");
        touch();
        raise(new TalentProfileContentChanged(id(), tenantId, TalentProfileContentChanged.Section.SKILLS));
    }

    public void replaceExperiences(List<ProfileContent.Experience> items) {
        ensureEditable();
        experiences = validatedItems(items, "experiences");
        touch();
        raise(new TalentProfileContentChanged(id(), tenantId, TalentProfileContentChanged.Section.EXPERIENCES));
    }

    public void replaceEducation(List<ProfileContent.Education> items) {
        ensureEditable();
        education = validatedItems(items, "education");
        touch();
        raise(new TalentProfileContentChanged(id(), tenantId, TalentProfileContentChanged.Section.EDUCATION));
    }

    public void replaceCredentials(List<ProfileContent.Credential> items) {
        ensureEditable();
        credentials = validatedItems(items, "credentials");
        touch();
        raise(new TalentProfileContentChanged(id(), tenantId, TalentProfileContentChanged.Section.CREDENTIALS));
    }

    public void replacePortfolio(List<ProfileContent.PortfolioItem> items) {
        ensureEditable();
        portfolio = validatedItems(items, "portfolio");
        touch();
        raise(new TalentProfileContentChanged(id(), tenantId, TalentProfileContentChanged.Section.PORTFOLIO));
    }

    private static <T> List<T> validatedItems(List<T> items, String section) {
        Objects.requireNonNull(items, section + " must not be null");
        if (items.size() > MAX_PROFILE_ITEMS) {
            throw new IllegalArgumentException(section + " cannot contain more than " + MAX_PROFILE_ITEMS + " items");
        }
        var copy = List.copyOf(items);
        var ids = new java.util.HashSet<UUID>();
        for (T item : copy) {
            Objects.requireNonNull(item, section + " item must not be null");
            UUID itemId;
            if (item instanceof ProfileContent.Skill value) itemId = value.id();
            else if (item instanceof ProfileContent.Experience value) itemId = value.id();
            else if (item instanceof ProfileContent.Education value) itemId = value.id();
            else if (item instanceof ProfileContent.Credential value) itemId = value.id();
            else if (item instanceof ProfileContent.PortfolioItem value) itemId = value.id();
            else throw new IllegalArgumentException("Unsupported " + section + " item type");
            if (!ids.add(itemId)) throw new IllegalArgumentException(section + " contains duplicate item IDs");
        }
        return copy;
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
    public List<ProfileContent.Skill> skills() { return skills; }
    public List<ProfileContent.Experience> experiences() { return experiences; }
    public List<ProfileContent.Education> education() { return education; }
    public List<ProfileContent.Credential> credentials() { return credentials; }
    public List<ProfileContent.PortfolioItem> portfolio() { return portfolio; }
}
