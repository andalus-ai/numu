package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Canonical, already-authenticated partner event envelope; raw request secrets are never persisted. */
public record PartnerWebhookEvent(UUID id, TenantId tenantId, UUID partnerId, String externalEventId,
                                  String eventType, String payloadJson, Instant occurredAt, Instant receivedAt) {
    public PartnerWebhookEvent {
        Objects.requireNonNull(id);Objects.requireNonNull(tenantId);Objects.requireNonNull(partnerId);
        externalEventId=required(externalEventId,"externalEventId");eventType=required(eventType,"eventType");
        payloadJson=required(payloadJson,"payloadJson");Objects.requireNonNull(occurredAt);Objects.requireNonNull(receivedAt);
        if(receivedAt.isBefore(occurredAt)) { /* delayed delivery is valid; timestamps are intentionally not ordered */ }
        if(!payloadJson.stripLeading().startsWith("{")) throw new IllegalArgumentException("payloadJson must be a JSON object");
    }
    private static String required(String v,String f){Objects.requireNonNull(v,f);String s=v.trim();if(s.isEmpty())throw new IllegalArgumentException(f+" must not be blank");return s;}
}