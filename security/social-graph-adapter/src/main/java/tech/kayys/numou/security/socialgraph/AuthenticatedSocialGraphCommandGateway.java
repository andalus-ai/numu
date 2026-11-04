package tech.kayys.numou.security.socialgraph;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.security.application.context.SecurityContextProvider;
import tech.kayys.numou.security.domain.authorization.SecurityContext;
import tech.kayys.numou.socialgraph.application.SocialGraphAccessDeniedException;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;

import java.util.Objects;
import java.util.Optional;

/** Request-facing command boundary; tenant and identity never come from command payloads. */
@ApplicationScoped
public class AuthenticatedSocialGraphCommandGateway {
    private final SecurityContextProvider securityContexts;
    private final VerifiedIdentitySubjectProvider identitySubjects;
    private final SocialGraphCommandInvoker commands;

    @Inject
    public AuthenticatedSocialGraphCommandGateway(
            SecurityContextProvider securityContexts,
            VerifiedIdentitySubjectProvider identitySubjects,
            SocialGraphCommandInvoker commands) {
        this.securityContexts = Objects.requireNonNull(securityContexts, "securityContexts cannot be null");
        this.identitySubjects = Objects.requireNonNull(identitySubjects, "identitySubjects cannot be null");
        this.commands = Objects.requireNonNull(commands, "commands cannot be null");
    }

    public Uni<SocialConnection> requestConnection(PartyId recipient, String key) {
        return withTrustedIdentity((tenant, subject) -> commands.requestConnection(tenant, subject, recipient, key));
    }
    public Uni<SocialConnection> acceptConnection(SocialConnectionId id, String key) {
        return withTrustedIdentity((tenant, subject) -> commands.acceptConnection(tenant, subject, id, key));
    }
    public Uni<SocialConnection> rejectConnection(SocialConnectionId id, String key) {
        return withTrustedIdentity((tenant, subject) -> commands.rejectConnection(tenant, subject, id, key));
    }
    public Uni<SocialConnection> cancelConnection(SocialConnectionId id, String key) {
        return withTrustedIdentity((tenant, subject) -> commands.cancelConnection(tenant, subject, id, key));
    }
    public Uni<SocialConnection> removeConnection(SocialConnectionId id, String key) {
        return withTrustedIdentity((tenant, subject) -> commands.removeConnection(tenant, subject, id, key));
    }

    private Uni<SocialConnection> withTrustedIdentity(TrustedCommand command) {
        SecurityContext context = currentContext();
        if (context == null || !context.authenticated() || !context.tenantMember()) return denied();
        Optional<IdentitySubjectRef> subject = identitySubjects.current();
        if (subject == null || subject.isEmpty()) return denied();
        return command.execute(context.tenantId(), subject.get());
    }

    private SecurityContext currentContext() {
        Optional<SecurityContext> current = securityContexts.current();
        return current == null ? null : current.orElse(null);
    }

    private static Uni<SocialConnection> denied() {
        return Uni.createFrom().failure(new SocialGraphAccessDeniedException());
    }

    @FunctionalInterface
    private interface TrustedCommand {
        Uni<SocialConnection> execute(TenantId tenant, IdentitySubjectRef subject);
    }
}
