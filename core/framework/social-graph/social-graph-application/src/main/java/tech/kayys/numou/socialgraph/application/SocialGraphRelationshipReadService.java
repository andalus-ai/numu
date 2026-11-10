package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipQueryPort;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipQueryPage;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipVisibilityPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Privacy-aware FOLLOW list reader.
 *
 * <p>The host supplies authoritative profile visibility and consent policy.
 * The service additionally removes pairs blocked in either direction. It
 * deliberately returns only Party references, never profile/contact fields.</p>
 */
public final class SocialGraphRelationshipReadService {
    private static final int MAX_PAGE_SIZE = 100;

    private final SocialGraphRelationshipQueryPort queries;
    private final SocialGraphRelationshipVisibilityPort visibility;
    private final SocialGraphPolicyService policies;
    private final Clock clock;

    public SocialGraphRelationshipReadService(
            SocialGraphRelationshipQueryPort queries,
            SocialGraphRelationshipVisibilityPort visibility,
            SocialGraphPolicyService policies,
            Clock clock) {
        this.queries = Objects.requireNonNull(queries, "queries cannot be null");
        this.visibility = Objects.requireNonNull(visibility, "visibility cannot be null");
        this.policies = Objects.requireNonNull(policies, "policies cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /** A Party's own following list. */
    public Uni<SocialGraphRelationshipReadPage> following(
            TenantId tenantId, PartyId viewer, String cursor, int limit) {
        return read(tenantId, viewer, viewer, cursor, limit, true);
    }

    /** Followers of a profile, subject to both profile-owner and follower visibility. */
    public Uni<SocialGraphRelationshipReadPage> followers(
            TenantId tenantId, PartyId viewer, PartyId profileOwner, String cursor, int limit) {
        return read(tenantId, viewer, profileOwner, cursor, limit, false);
    }

    private Uni<SocialGraphRelationshipReadPage> read(
            TenantId tenantId, PartyId viewer, PartyId relationshipOwner,
            String cursor, int limit, boolean outgoing) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(viewer, "viewer cannot be null");
        Objects.requireNonNull(relationshipOwner, "relationshipOwner cannot be null");
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        Instant now = clock.instant();
        return visibility.canViewProfile(tenantId, viewer, relationshipOwner)
                .flatMap(ownerVisible -> {
                    if (!ownerVisible) {
                        // Fail closed without disclosing whether the profile or list exists.
                        return Uni.createFrom().item(new SocialGraphRelationshipReadPage(java.util.List.of(), null));
                    }
                    Uni<SocialGraphRelationshipQueryPage> raw = outgoing
                            ? queries.findFollowing(tenantId, relationshipOwner, now, cursor, limit)
                            : queries.findFollowers(tenantId, relationshipOwner, now, cursor, limit);
                    return raw.flatMap(page -> filterVisible(tenantId, viewer, page)
                            .map(items -> new SocialGraphRelationshipReadPage(items, page.nextCursor())));
                });
    }

    private Uni<java.util.List<SocialGraphRelationshipReadEntry>> filterVisible(
            TenantId tenantId, PartyId viewer, SocialGraphRelationshipQueryPage page) {
        return Multi.createFrom().iterable(page.items())
                .onItem().transformToUniAndConcatenate(item ->
                        visibility.canViewProfile(tenantId, viewer, item.partyId())
                                .flatMap(canView -> {
                                    if (!canView) return Uni.createFrom().item(Optional.<SocialGraphRelationshipReadEntry>empty());
                                    return policies.isBlocked(tenantId, viewer, item.partyId())
                                            .map(blocked -> blocked
                                                    ? Optional.<SocialGraphRelationshipReadEntry>empty()
                                                    : Optional.of(new SocialGraphRelationshipReadEntry(
                                                            item.relationshipId(), item.partyId(), item.createdAt())));
                                }))
                .collect().asList()
                .map(results -> results.stream().flatMap(Optional::stream).toList());
    }
}
