package tech.kayys.numou.talent.application.api;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyRef;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.application.port.*;
import tech.kayys.numou.talent.domain.profile.*;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Application boundary for tenant-scoped talent profiles.
 *
 * <p>Tenant and actor are taken only from trusted ExecutionContext supplied by
 * the host's authenticated request pipeline. The service never accepts tenant
 * identity in a command payload.</p>
 */
public final class TalentProfileApplicationService {

    private final TalentProfileRepository profiles;
    private final PersonPartyDirectory parties;
    private final TalentProfileAccessPolicy access;
    private final ProfileEvidenceVerifier evidenceVerifier;
    private final TalentProfileTransactionRunner transactions;

    public TalentProfileApplicationService(
            TalentProfileRepository profiles,
            PersonPartyDirectory parties,
            TalentProfileAccessPolicy access,
            ProfileEvidenceVerifier evidenceVerifier,
            TalentProfileTransactionRunner transactions
    ) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.access = Objects.requireNonNull(access, "access");
        this.evidenceVerifier = Objects.requireNonNull(evidenceVerifier, "evidenceVerifier");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public Uni<TalentProfileView> create(
            ExecutionContext context,
            TalentProfileId profileId,
            PartyRef subject,
            String headline,
            String summary,
            String locationLabel
    ) {
        TenantId tenantId = requireTrustedContext(context);
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(subject, "subject");

        if (subject.kind() != tech.kayys.numou.ecosystem.domain.party.PartyKind.PERSON) {
            return Uni.createFrom().failure(new IllegalArgumentException("Talent profile subject must be a PERSON Party"));
        }

        return parties.existsPerson(tenantId, subject).flatMap(exists -> {
            if (!Boolean.TRUE.equals(exists)) {
                return Uni.createFrom().failure(new IllegalArgumentException("PERSON Party does not exist in the active tenant"));
            }
            return allowed(context, TalentProfileAccessPolicy.Action.CREATE, subject, null)
                    .flatMap(ignored -> transactions.inTransaction(context, tx -> {
                        TalentProfile profile = TalentProfile.create(profileId, tenantId, subject,
                                headline, summary, locationLabel);
                        List<DomainEvent> events = profile.pullDomainEvents();
                        return tx.profiles().insert(tenantId, profile)
                                .flatMap(saved -> tx.publish(context, events)
                                        .replaceWith(TalentProfileView.from(saved)));
                    }));
        });
    }

    public Uni<TalentProfileView> updatePresentation(
            ExecutionContext context, TalentProfileId id,
            String headline, String summary, String locationLabel
    ) {
        return mutate(context, id, TalentProfileAccessPolicy.Action.UPDATE, profile ->
                profile.updatePresentation(headline, summary, locationLabel));
    }

    public Uni<TalentProfileView> replaceSkills(
            ExecutionContext context, TalentProfileId id, List<ProfileContent.Skill> skills
    ) {
        List<ProfileContent.Skill> safe = List.copyOf(Objects.requireNonNull(skills, "skills"));
        return mutateAsync(context, id, TalentProfileAccessPolicy.Action.UPDATE, profile ->
                verifySkills(context, profile.subject(), safe, 0).invoke(() -> profile.replaceSkills(safe)));
    }

    public Uni<TalentProfileView> replaceExperiences(
            ExecutionContext context, TalentProfileId id, List<ProfileContent.Experience> experiences
    ) {
        List<ProfileContent.Experience> safe = List.copyOf(Objects.requireNonNull(experiences, "experiences"));
        return mutate(context, id, TalentProfileAccessPolicy.Action.UPDATE, profile -> profile.replaceExperiences(safe));
    }

    public Uni<TalentProfileView> replaceEducation(
            ExecutionContext context, TalentProfileId id, List<ProfileContent.Education> education
    ) {
        List<ProfileContent.Education> safe = List.copyOf(Objects.requireNonNull(education, "education"));
        return mutate(context, id, TalentProfileAccessPolicy.Action.UPDATE, profile -> profile.replaceEducation(safe));
    }

    public Uni<TalentProfileView> replaceCredentials(
            ExecutionContext context, TalentProfileId id, List<ProfileContent.Credential> credentials
    ) {
        List<ProfileContent.Credential> safe = List.copyOf(Objects.requireNonNull(credentials, "credentials"));
        return mutateAsync(context, id, TalentProfileAccessPolicy.Action.UPDATE, profile ->
                verifyCredentials(context, profile.subject(), safe, 0).invoke(() -> profile.replaceCredentials(safe)));
    }

    public Uni<TalentProfileView> replacePortfolio(
            ExecutionContext context, TalentProfileId id, List<ProfileContent.PortfolioItem> portfolio
    ) {
        List<ProfileContent.PortfolioItem> safe = List.copyOf(Objects.requireNonNull(portfolio, "portfolio"));
        return mutate(context, id, TalentProfileAccessPolicy.Action.UPDATE, profile -> profile.replacePortfolio(safe));
    }

    public Uni<TalentProfileView> changeVisibility(
            ExecutionContext context, TalentProfileId id, ProfileVisibility visibility
    ) {
        Objects.requireNonNull(visibility, "visibility");
        return mutate(context, id, TalentProfileAccessPolicy.Action.CHANGE_VISIBILITY,
                profile -> profile.changeVisibility(visibility));
    }

    public Uni<TalentProfileView> publish(ExecutionContext context, TalentProfileId id) {
        return mutate(context, id, TalentProfileAccessPolicy.Action.PUBLISH, TalentProfile::publish);
    }

    /** Authorization is evaluated before the immutable view is constructed or returned. */
    public Uni<TalentProfileView> get(ExecutionContext context, TalentProfileId id) {
        TenantId tenantId = requireTrustedContext(context);
        Objects.requireNonNull(id, "id");
        return profiles.findByTenantAndId(tenantId, id).flatMap(found -> {
            if (found.isEmpty()) return Uni.createFrom().failure(new TalentProfileNotFoundException(id));
            TalentProfile profile = found.get();
            TalentProfileAccessPolicy.Action action = switch (profile.visibility()) {
                case PRIVATE -> TalentProfileAccessPolicy.Action.READ_PRIVATE;
                case NETWORK -> TalentProfileAccessPolicy.Action.READ_NETWORK;
                case PUBLIC -> TalentProfileAccessPolicy.Action.READ_PUBLIC;
            };
            return allowed(context, action, profile.subject(), profile)
                    .map(ignored -> TalentProfileView.from(profile));
        });
    }

    private Uni<Void> verifySkills(
            ExecutionContext context, PartyRef subject, List<ProfileContent.Skill> skills, int index
    ) {
        if (index >= skills.size()) return Uni.createFrom().voidItem();
        ProfileContent.Skill skill = skills.get(index);
        if (skill.verification() == ProfileContent.Verification.SELF_REPORTED) {
            return verifySkills(context, subject, skills, index + 1);
        }
        return evidenceVerifier.verifySkill(context, subject, skill).flatMap(verified -> {
            if (!Boolean.TRUE.equals(verified)) {
                return Uni.createFrom().failure(new SecurityException("Skill verification evidence was not accepted"));
            }
            return verifySkills(context, subject, skills, index + 1);
        });
    }

    private Uni<Void> verifyCredentials(
            ExecutionContext context, PartyRef subject, List<ProfileContent.Credential> credentials, int index
    ) {
        if (index >= credentials.size()) return Uni.createFrom().voidItem();
        ProfileContent.Credential credential = credentials.get(index);
        if (credential.verification() == ProfileContent.Verification.SELF_REPORTED) {
            return verifyCredentials(context, subject, credentials, index + 1);
        }
        return evidenceVerifier.verifyCredential(context, subject, credential).flatMap(verified -> {
            if (!Boolean.TRUE.equals(verified)) {
                return Uni.createFrom().failure(new SecurityException("Credential verification evidence was not accepted"));
            }
            return verifyCredentials(context, subject, credentials, index + 1);
        });
    }

    private Uni<TalentProfileView> mutateAsync(
            ExecutionContext context,
            TalentProfileId id,
            TalentProfileAccessPolicy.Action action,
            java.util.function.Function<TalentProfile, Uni<Void>> mutation
    ) {
        TenantId tenantId = requireTrustedContext(context);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mutation, "mutation");
        return transactions.inTransaction(context, tx ->
                tx.profiles().findByTenantAndId(tenantId, id).flatMap(found -> {
                    if (found.isEmpty()) return Uni.createFrom().failure(new TalentProfileNotFoundException(id));
                    TalentProfile profile = found.get();
                    int expectedVersion = profile.getVersion();
                    return allowed(context, action, profile.subject(), profile).flatMap(ignored ->
                            mutation.apply(profile).flatMap(done -> {
                                List<DomainEvent> events = profile.pullDomainEvents();
                                return tx.profiles().update(tenantId, profile, expectedVersion)
                                        .flatMap(saved -> tx.publish(context, events)
                                                .replaceWith(TalentProfileView.from(saved)));
                            }));
                }));
    }

    private Uni<TalentProfileView> mutate(
            ExecutionContext context,
            TalentProfileId id,
            TalentProfileAccessPolicy.Action action,
            Consumer<TalentProfile> mutation
    ) {
        TenantId tenantId = requireTrustedContext(context);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mutation, "mutation");

        return transactions.inTransaction(context, tx ->
                tx.profiles().findByTenantAndId(tenantId, id).flatMap(found -> {
                    if (found.isEmpty()) return Uni.createFrom().failure(new TalentProfileNotFoundException(id));
                    TalentProfile profile = found.get();
                    int expectedVersion = profile.getVersion();
                    return allowed(context, action, profile.subject(), profile).flatMap(ignored -> {
                        mutation.accept(profile);
                        List<DomainEvent> events = profile.pullDomainEvents();
                        return tx.profiles().update(tenantId, profile, expectedVersion)
                                .flatMap(saved -> tx.publish(context, events)
                                        .replaceWith(TalentProfileView.from(saved)));
                    });
                }));
    }

    private Uni<Void> allowed(
            ExecutionContext context,
            TalentProfileAccessPolicy.Action action,
            PartyRef subject,
            TalentProfile profile
    ) {
        return access.isAllowed(context, action, subject, profile).flatMap(allowed -> {
            if (Boolean.TRUE.equals(allowed)) return Uni.createFrom().voidItem();
            return Uni.createFrom().failure(new TalentProfileAccessDeniedException(action.name()));
        });
    }

    private static TenantId requireTrustedContext(ExecutionContext context) {
        Objects.requireNonNull(context, "execution context");
        TenantId tenantId = context.requireTenant();
        String actor = context.actorId().value();
        if ("anonymous".equals(actor)) {
            throw new SecurityException("Authenticated actor context is required for talent profile operations");
        }
        return tenantId;
    }
}
