package tech.kayys.numou.omnichannel.core;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.Optional;

/**
 * Trust-boundary context resolved by an authenticated adapter before business
 * commands are dispatched. The caller must not be allowed to self-assert a
 * tenant or branch without authorization.
 */
public record ChannelContext(
        TenantId tenantId,
        Optional<String> branchId,
        ChannelType channelType,
        String providerAccountId,
        String correlationId) {

    public ChannelContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        branchId = Objects.requireNonNull(branchId, "branchId must not be null")
                .map(value -> required(value, "branchId"));
        Objects.requireNonNull(channelType, "channelType must not be null");
        providerAccountId = required(providerAccountId, "providerAccountId");
        correlationId = required(correlationId, "correlationId");
    }

    public static ChannelContext of(
            TenantId tenantId,
            String branchId,
            ChannelType channelType,
            String providerAccountId,
            String correlationId) {
        return new ChannelContext(
                tenantId,
                Optional.ofNullable(branchId).map(String::trim).filter(value -> !value.isEmpty()),
                channelType,
                providerAccountId,
                correlationId);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
