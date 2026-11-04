package tech.kayys.numou.identity.application.port;
import java.util.Set;
import java.util.UUID;
/** Issues an access token for an authenticated, tenant-bound OAuth client. */
public interface OAuthAccessTokenIssuerPort {
    String issueClientAccessToken(String clientId, UUID tenantId, UUID applicationId, Set<String> scopes, long credentialVersion);
}
