package tech.kayys.numou.identity.adapter.outbound.idp.ldap;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.identity.application.dto.FederationSettings;
import tech.kayys.numou.identity.application.port.FederationSettingsPort;
import tech.kayys.numou.identity.application.port.DirectoryAuthPort;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.user.FederatedIdentity;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class LdapDirectoryAdapter implements DirectoryAuthPort {

    private static final Logger LOG = Logger.getLogger(LdapDirectoryAdapter.class);

    private final FederationSettingsPort settingsPort;
    private final ExecutorService directory = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ldap-directory");
        thread.setDaemon(true);
        return thread;
    });

    @Inject
    public LdapDirectoryAdapter(FederationSettingsPort settingsPort) {
        this.settingsPort = settingsPort;
    }

    @Override
    public Uni<FederatedIdentity> authenticate(IdentityProviderConfig config, String username, String password) {
        Objects.requireNonNull(config, "config cannot be null");
        FederationSettings settings = settingsPort.parse(config);
        if (settings.ldapUrl().isBlank() || settings.ldapBaseDn().isBlank()) {
            return Uni.createFrom().failure(ApplicationError.of(
                    "LDAP_MISCONFIGURED",
                    "connectionConfigJson must contain ldapUrl and ldapBaseDn").toException());
        }

        return Uni.createFrom()
                .<FederatedIdentity>completionStage(() -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return authenticateBlocking(
                                config.id().value().toString(), settings, username, password);
                    } catch (NamingException e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                }, directory))
                .onFailure()
                .transform(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof AuthenticationException) {
                        return ApplicationError.of("LDAP_AUTH_FAILED", "Invalid directory credentials").toException();
                    }
                    if (cause instanceof CommunicationException) {
                        return ApplicationError.of("LDAP_UNAVAILABLE", "Directory server unreachable").toException();
                    }
                    return ApplicationError.of("LDAP_ERROR", cause.getClass().getSimpleName()).toException();
                });
    }

    private static FederatedIdentity authenticateBlocking(
            String providerId, FederationSettings settings, String username, String password)
            throws NamingException {

        String userDn = findUserDn(settings, username);
        bindAsUser(settings, userDn, password); // fails closed on invalid credentials

        List<String> groups = listGroups(settings, userDn);
        Attributes attributes = readAttributes(settings, userDn);

        String email = firstValue(attributes.get("mail"));
        String displayName = firstValue(attributes.get("displayName"));
        if (displayName == null) {
            displayName = firstValue(attributes.get("cn"));
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = username;
        }

        return new FederatedIdentity(
                providerId,
                username,
                email == null ? "" : email,
                displayName,
                Set.copyOf(groups),
                Map.of("userDn", userDn)
        );
    }

    private static DirContext bindServiceAccount(FederationSettings settings) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(javax.naming.Context.PROVIDER_URL, settings.ldapUrl());
        String bindDn = settings.ldapBindDn();
        String bindPassword = settings.ldapBindPassword();
        if (bindDn != null && !bindDn.isBlank()) {
            env.put(javax.naming.Context.SECURITY_PRINCIPAL, bindDn);
            env.put(javax.naming.Context.SECURITY_CREDENTIALS, bindPassword == null ? "" : bindPassword);
            env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
        }
        return new InitialDirContext(env);
    }

    /** First entry matching the user search filter; its name-in-namespace is the bind DN. */
    private static String findUserDn(FederationSettings settings, String username) throws NamingException {
        DirContext ctx = null;
        try {
            ctx = bindServiceAccount(settings);
            String filter = settings.ldapUserSearchFilter().replace("{0}", escape(username));
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[] { "dn", "cn", "mail", "displayName", "sAMAccountName", "uid" });
            NamingEnumeration<SearchResult> results =
                    ctx.search(settings.ldapBaseDn(), filter, controls);
            if (!results.hasMore()) {
                throw new AuthenticationException("No directory entry matches username '" + username + "'");
            }
            return results.next().getNameInNamespace();
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    /** Proves possession of the user's password by binding as their DN. */
    private static void bindAsUser(FederationSettings settings, String userDn, String password)
            throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(javax.naming.Context.PROVIDER_URL, settings.ldapUrl());
        env.put(javax.naming.Context.SECURITY_PRINCIPAL, userDn);
        env.put(javax.naming.Context.SECURITY_CREDENTIALS, password == null ? "" : password);
        env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    /** Groups whose member/memberUid/uniqueMember attribute references the user DN. */
    private static List<String> listGroups(FederationSettings settings, String userDn) throws NamingException {
        DirContext ctx = null;
        try {
            ctx = bindServiceAccount(settings);
            String filter = settings.ldapGroupFilter().replace("{0}", escape(userDn));
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[] { "cn" });
            NamingEnumeration<SearchResult> results =
                    ctx.search(settings.ldapGroupSearchBase(), filter, controls);
            List<String> groups = new ArrayList<>();
            while (results.hasMore()) {
                Attributes attributes = results.next().getAttributes();
                Attribute cn = attributes.get("cn");
                if (cn != null) {
                    for (NamingEnumeration<?> values = cn.getAll(); values.hasMore(); ) {
                        groups.add(String.valueOf(values.next()));
                    }
                }
            }
            return groups;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    /** Read-only re-bind with the service account to collect informational attributes. */
    private static Attributes readAttributes(FederationSettings settings, String userDn) throws NamingException {
        DirContext ctx = null;
        try {
            ctx = bindServiceAccount(settings);
            return ctx.getAttributes(userDn, new String[] { "mail", "displayName", "cn" });
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    /** Groups the directory entry belongs to (service-account bind). */
    @Override
    public Uni<List<String>> groupsFor(IdentityProviderConfig config, String userDn) {
        Objects.requireNonNull(config, "config cannot be null");
        FederationSettings settings = settingsPort.parse(config);
        return Uni.createFrom()
                .<List<String>>completionStage(() -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return listGroups(settings, userDn);
                    } catch (NamingException e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                }, directory))
                .onFailure()
                .transform(ex -> ApplicationError.of("LDAP_ERROR",
                        (ex.getCause() != null ? ex.getCause() : ex).getClass().getSimpleName()).toException());
    }

    private static String firstValue(Attribute attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            NamingEnumeration<?> values = attribute.getAll();
            if (!values.hasMore()) {
                return null;
            }
            Object value = values.next();
            return value == null ? null : String.valueOf(value);
        } catch (NamingException ex) {
            return null;
        }
    }

    /** RFC 4514 escape for the characters that matter in filter values here. */
    private static String escape(String value) {
        return value.replace("\\", "\\5c").replace("*", "\\2a").replace("(", "\\28")
                .replace(")", "\\29").replace("\u0000", "\\00");
    }

    // APPEND-POINT
}
