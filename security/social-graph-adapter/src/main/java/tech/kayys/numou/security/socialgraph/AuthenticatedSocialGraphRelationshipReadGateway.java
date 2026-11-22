package tech.kayys.numou.security.socialgraph;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.security.application.context.SecurityContextProvider;
import tech.kayys.numou.security.domain.authorization.Permission;
import tech.kayys.numou.security.domain.authorization.SecurityContext;
import tech.kayys.numou.socialgraph.application.SocialGraphAccessDeniedException;
import tech.kayys.numou.socialgraph.application.SocialGraphRelationshipReadPage;

import java.util.Objects;
import java.util.Optional;

/**
 * Request-facing relationship-read boundary. The viewer Party is resolved
 * downstream from the verified tenant and issuer/subject, never supplied by
 * the caller. The caller may supply a profile owner only as a read target.
 */
@ApplicationScoped
public class AuthenticatedSocialGraphRelationshipReadGateway {
    private static final Permission READ_PERMISSION = Permission.of("social-graph", "read");

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

    /** Reads followers of a target profile; the authenticated viewer is never caller-controlled. */
    public Uni<SocialGraphRelationshipReadPage> followers(PartyId profileOwner, String cursor, int limit) {
        Objects.requireNonNull(profileOwner, "profileOwner cannot be null");
        return withTrustedIdentity((tenant, subject) ->
                reads.followers(tenant, subject, profileOwner, cursor, limit));
    }

    private Uni<SocialGraphRelationshipReadPage> withTrustedIdentity(TrustedRead read) {
        Optional<SecurityContext> current = securityContexts.current();
        SecurityContext context = current == null ? null : current.orElse(null);
        if (context == null || !context.authenticated() || !context.tenantMember()
                || !context.hasPermission(READ_PERMISSION)) {
            return denied();
        }

        Optional<tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef> subject = identitySubjects.current();
        if (subject == null || subject.isEmpty()) return denied();
        return read.execute(context.tenantId(), subject.get());
    }

    private static Uni<SocialGraphRelationshipReadPage> denied() {
        return Uni.createFrom().failure(new SocialGraphAccessDeniedException());
    }

    @FunctionalInterface
    private interface TrustedRead {
        Uni<SocialGraphRelationshipReadPage> execute(
                tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject);
    }
}
