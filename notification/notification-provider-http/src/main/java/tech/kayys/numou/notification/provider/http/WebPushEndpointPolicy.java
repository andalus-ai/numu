package tech.kayys.numou.notification.provider.http;

import java.net.URI;
import java.util.UUID;

/**
 * Host-owned egress policy. Implementations must validate resolved addresses and
 * enforce DNS-rebinding-safe egress controls/allowlists for the tenant.
 */
@FunctionalInterface
public interface WebPushEndpointPolicy {
    boolean isAllowed(UUID tenantId, URI endpoint);
}
