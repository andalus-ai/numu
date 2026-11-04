package tech.kayys.numou.talent.application.api;

import tech.kayys.numou.talent.domain.profile.*;

import java.time.Instant;
import java.util.List;

/** Immutable application read model; callers never receive the mutable aggregate instance. */
public record TalentProfileView(
        TalentProfileId id,
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
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public TalentProfileView {
        skills = List.copyOf(skills);
        experiences = List.copyOf(experiences);
        education = List.copyOf(education);
        credentials = List.copyOf(credentials);
        portfolio = List.copyOf(portfolio);
    }

    public static TalentProfileView from(TalentProfile profile) {
        return new TalentProfileView(profile.id(), profile.headline(), profile.summary(),
                profile.locationLabel(), profile.visibility(), profile.status(), profile.skills(),
                profile.experiences(), profile.education(), profile.credentials(), profile.portfolio(),
                profile.getVersion(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
