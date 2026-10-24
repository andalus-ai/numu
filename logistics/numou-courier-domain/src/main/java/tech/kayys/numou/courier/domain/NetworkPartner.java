package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Tenant-owned carrier, franchise, agent or delivery-network partner. */
public record NetworkPartner(UUID id, TenantId tenantId, String partnerCode, String displayName,
                             NetworkPartnerStatus status, Set<String> supportedServices,
                             Instant createdAt, Instant updatedAt) {
    public NetworkPartner {
        Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(status);
        partnerCode=required(partnerCode,"partnerCode").toUpperCase(Locale.ROOT);
        displayName=required(displayName,"displayName");
        supportedServices=Set.copyOf(Objects.requireNonNull(supportedServices,"supportedServices")).stream()
                .map(s->required(s,"supported service").toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if(updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        if(supportedServices.isEmpty()) throw new IllegalArgumentException("at least one supported service is required");
    }
    public boolean canAccept(String service){return status==NetworkPartnerStatus.ACTIVE && supportedServices.contains(required(service,"service").toUpperCase(Locale.ROOT));}
    private static String required(String v,String f){Objects.requireNonNull(v,f);String s=v.trim();if(s.isEmpty())throw new IllegalArgumentException(f+" must not be blank");return s;}
}