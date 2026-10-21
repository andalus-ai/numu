package tech.kayys.numou.identity.application.dto;

import java.util.List;
import java.util.Map;

/**
 * Federation settings decoded from an identity provider's {@code connectionConfigJson}.
 *
 * <p>Endpoints may be left blank when {@code issuer} supports OIDC Discovery
 * ({@code /.well-known/openid-configuration}); the OIDC adapter fills them from discovery.
 *
 * @param issuer             OIDC issuer URL (also the discovery base)
 * @param clientId           OAuth2 client id
 * @param clientSecret       OAuth2 client secret (confidential client); blank for public clients
 * @param redirectUri        registered callback URL this service sends to the provider
 * @param scopes             requested scopes; defaults to {@code openid email profile}
 * @param authorizationEndpoint  explicit authorization endpoint (overrides discovery)
 * @param tokenEndpoint      explicit token endpoint (overrides discovery)
 * @param jwksUri            explicit JWKS URI (overrides discovery)
 * @param groupRoleMappings  provider group name -> internal role id to grant on login
 * @param defaultScopePath   scope path those role grants are written at (required with mappings)
 * @param defaultScopeType   scope type of those grants: SYSTEM | ORGANIZATION | WORKSPACE
 * @param ldapUrl            LDAP server URL, e.g. {@code ldaps://ad.corp.example:636}
 * @param ldapBaseDn         search base, e.g. {@code ou=people,dc=corp,dc=example}
 * @param ldapBindDn         service-account DN for searches (blank = anonymous search)
 * @param ldapBindPassword   service-account password (blank = none)
 * @param ldapUserSearchFilter user lookup filter with a {@code {0}} placeholder for the login,
 *                           e.g. {@code (&(objectClass=user)(sAMAccountName={0}))}
 * @param ldapGroupSearchBase  base DN for group lookups (defaults to {@code ldapBaseDn})
 * @param ldapGroupFilter    group search filter with a {@code {0}} placeholder for the user DN
 * @param samlEntityId       this service's SP entity id
 * @param samlAcsUrl         assertion consumer URL (defaults to the provider's callback URL)
 * @param samlIdpSsoUrl      IdP single-sign-on URL (where the browser is sent to log in)
 * @param samlIdpCertificatePem IdP X.509 signing certificate (PEM) responses are verified against
 * @param samlNameIdAttributes NameID attribute names accepted as the subject, in order
 * @param samlAttributeToGroups SAML attribute names whose values become groups, in order
 */
public record FederationSettings(
        String issuer,
        String clientId,
        String clientSecret,
        String redirectUri,
        List<String> scopes,
        String authorizationEndpoint,
        String tokenEndpoint,
        String jwksUri,
        Map<String, String> groupRoleMappings,
        String defaultScopePath,
        String defaultScopeType,
        String ldapUrl,
        String ldapBaseDn,
        String ldapBindDn,
        String ldapBindPassword,
        String ldapUserSearchFilter,
        String ldapGroupSearchBase,
        String ldapGroupFilter,
        String samlEntityId,
        String samlAcsUrl,
        String samlIdpSsoUrl,
        String samlIdpCertificatePem,
        List<String> samlNameIdAttributes,
        List<String> samlAttributeToGroups
) {

    public FederationSettings {
        issuer = issuer == null ? "" : issuer.trim();
        clientId = clientId == null ? "" : clientId.trim();
        clientSecret = clientSecret == null ? "" : clientSecret;
        redirectUri = redirectUri == null ? "" : redirectUri.trim();
        scopes = scopes == null || scopes.isEmpty()
                ? List.of("openid", "email", "profile")
                : List.copyOf(scopes);
        authorizationEndpoint = authorizationEndpoint == null ? "" : authorizationEndpoint.trim();
        tokenEndpoint = tokenEndpoint == null ? "" : tokenEndpoint.trim();
        jwksUri = jwksUri == null ? "" : jwksUri.trim();
        groupRoleMappings = groupRoleMappings == null ? Map.of() : Map.copyOf(groupRoleMappings);
        defaultScopePath = defaultScopePath == null ? "" : defaultScopePath.trim();
        defaultScopeType = defaultScopeType == null || defaultScopeType.isBlank()
                ? "ORGANIZATION"
                : defaultScopeType.trim();
        ldapUrl = ldapUrl == null ? "" : ldapUrl.trim();
        ldapBaseDn = ldapBaseDn == null ? "" : ldapBaseDn.trim();
        ldapBindDn = ldapBindDn == null ? "" : ldapBindDn.trim();
        ldapBindPassword = ldapBindPassword == null ? "" : ldapBindPassword;
        ldapUserSearchFilter = ldapUserSearchFilter == null || ldapUserSearchFilter.isBlank()
                ? "(&(objectClass=person)(|(sAMAccountName={0})(uid={0})(userPrincipalName={0})))"
                : ldapUserSearchFilter.trim();
        ldapGroupSearchBase = ldapGroupSearchBase == null || ldapGroupSearchBase.isBlank()
                ? ldapBaseDn
                : ldapGroupSearchBase.trim();
        // Group lookup is a reverse-membership search by default: find groups whose member/memberUid
        // attribute points at the user DN. Directory-specific filters can override it.
        ldapGroupFilter = ldapGroupFilter == null || ldapGroupFilter.isBlank()
                ? "(|(member={0})(memberUid={0})(uniqueMember={0}))"
                : ldapGroupFilter.trim();
        samlEntityId = samlEntityId == null ? "" : samlEntityId.trim();
        samlAcsUrl = samlAcsUrl == null ? "" : samlAcsUrl.trim();
        samlIdpSsoUrl = samlIdpSsoUrl == null ? "" : samlIdpSsoUrl.trim();
        samlIdpCertificatePem = samlIdpCertificatePem == null ? "" : samlIdpCertificatePem.trim();
        samlNameIdAttributes = samlNameIdAttributes == null || samlNameIdAttributes.isEmpty()
                ? List.of("Email", "email", "mail", "NameID")
                : List.copyOf(samlNameIdAttributes);
        samlAttributeToGroups = samlAttributeToGroups == null || samlAttributeToGroups.isEmpty()
                ? List.of("Role", "Groups", "groups", "memberOf")
                : List.copyOf(samlAttributeToGroups);
    }

    /** Returns a copy with the endpoints discovered from the provider's metadata document. */
    public FederationSettings withDiscoveredEndpoints(String authorizationEndpoint, String tokenEndpoint, String jwksUri) {
        return new FederationSettings(
                issuer,
                clientId,
                clientSecret,
                redirectUri,
                scopes,
                this.authorizationEndpoint.isBlank() ? authorizationEndpoint : this.authorizationEndpoint,
                this.tokenEndpoint.isBlank() ? tokenEndpoint : this.tokenEndpoint,
                this.jwksUri.isBlank() ? jwksUri : this.jwksUri,
                groupRoleMappings,
                defaultScopePath,
                defaultScopeType,
                ldapUrl,
                ldapBaseDn,
                ldapBindDn,
                ldapBindPassword,
                ldapUserSearchFilter,
                ldapGroupSearchBase,
                ldapGroupFilter,
                samlEntityId,
                samlAcsUrl,
                samlIdpSsoUrl,
                samlIdpCertificatePem,
                samlNameIdAttributes,
                samlAttributeToGroups
        );
    }
}
