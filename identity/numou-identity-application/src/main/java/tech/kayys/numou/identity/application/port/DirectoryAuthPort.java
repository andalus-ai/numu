package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.user.FederatedIdentity;

import java.util.List;

/**
 * SPI for an LDAP/Active Directory server (JNDI in the adapter, zero third-party dependencies).
 *
 * <p>Two responsibilities, deliberately split: direct credential verification (bind-as-user) and
 * read-only directory access for group lookups and background synchronization.
 */
public interface DirectoryAuthPort {

    /**
     * Authenticates one directory entry: searches for the user, binds with their DN + password,
     * then reads the entry's groups - all with raw credentials that must never be logged.
     *
     * @param config   identity provider configuration (connection + search settings)
     * @param username directory login name (sAMAccountName, uid, or userPrincipalName)
     * @param password raw password (handled in-memory only, never persisted or logged)
     * @return the directory entry as a neutral {@link FederatedIdentity}
     */
    Uni<FederatedIdentity> authenticate(IdentityProviderConfig config, String username, String password);

    /**
     * Lists the groups one entry belongs to without verifying credentials - for scheduled
     * synchronization and group-to-role re-evaluation. Binds with the configured service account.
     *
     * @param config identity provider configuration
     * @param userDn distinguished name of the entry
     * @return group names (common names, e.g. {@code "Finance-Admins"})
     */
    Uni<List<String>> groupsFor(IdentityProviderConfig config, String userDn);
}
