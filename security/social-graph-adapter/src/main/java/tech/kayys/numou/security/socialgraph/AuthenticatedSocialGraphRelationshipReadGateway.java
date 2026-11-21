package tech.kayys.numou.security.socialgraph;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.security.application.context.SecurityContextProvider;
import tech.kayys.numou.security.domain.authorization.Permission;
import tech.kayys.numou.security.domain.authorization.SecurityContext;
import tech.kayys.numou.socialgraph.application.SocialGraphAccessDeniedException;
import tech.kayys.numou.socialgraph.application.SocialGraphRelationshipReadPage;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Request-facing read boundary. Tenant, actor identity and read permission are
 * derived from the verified request context, never from query parameters.
 */
@ApplicationScoped
public class AuthenticatedSocialGraphRelationshipReadGateway {
    private static final Permission READ_RELATIONSHIPS = Permission.of("social-graph", "read");

    private final SecurityContextProvider securityContexts;
    private final VerifiedIdentitySubjectProvider identitySubjects;
    private final SocialGraphRelationshipReadInvoker reads;

    @Inject
    public AuthenticatedSocialGraphRelationshipReadGateway(
            SecurityContextProvider securityContexts,
            VerifiedIdentitySubjectProvider identitySubjects,
            SocialGraphRelationshipReadInvoker reads) {
        this.securityContexts = Objects.requireNonNull(securityContexts, "securityContexts cannot be null");
        this.identitySubjects = Objects.requireNonNull(identitySubjects, "identitySubjects cannot be null");
        this.reads = Objects.requireNonNull(reads, "reads cannot be null");
    }

    /** Reads the authenticated Party's own following list. */
    public Uni<SocialGraphRelationshipReadPage> following(String cursor, int limit) {
        return withTrustedIdentity((tenant, subject) -> reads.following(tenant, subject, cursor, limit));
    }

    /** Reads a profile owner's followers; profileOwner is a target, never the viewer. */
    public Uni<SocialGraphRelationshipReadPage> followers(PartyId profileOwner, String cursor, int limit) {
        if (profileOwner == null) return denied();
        return withTrustedIdentity((tenant, subject) -> reads.followers(tenant, subject, profileOwner, cursor, limit));
    }

    private Uni<SocialGraphRelationshipReadPage> withTrustedIdentity(
            BiFunction<TenantId, IdentitySubjectRef, Uni<SocialGraphRelationshipReadPage>> read) {
        Optional<SecurityContext> current = securityContexts.current();
        SecurityContext context = current == null ? null : current.orElse(null);
        if (context == null || !context.authenticated() || !context.tenantMember()
                || !context.hasPermission(READ_RELATIONSHIPS)) {
            return denied();
        }

        Optional<IdentitySubjectRef> subject = identitySubjects.current();
        if (subject == null || subject.isEmpty()) return denied();
        return read.apply(context.tenantId(), subject.get());
    }

    private static Uni<SocialGraphRelationshipReadPage> denied() {
        return Uni.createFrom().failure(new SocialGraphAccessDeniedException());
    }
}
