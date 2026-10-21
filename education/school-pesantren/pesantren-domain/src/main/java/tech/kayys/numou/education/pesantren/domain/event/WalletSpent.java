package tech.kayys.numou.education.pesantren.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.education.pesantren.domain.SantriWalletId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record WalletSpent(
        UUID eventId,
        Instant occurredAt,
        SantriWalletId walletId,
        TenantId tenantId,
        SantriId santriId,
        BigDecimal amount
) implements DomainEvent {
    public WalletSpent(SantriWalletId walletId, TenantId tenantId, SantriId santriId, BigDecimal amount) {
        this(UUID.randomUUID(), Instant.now(), walletId, tenantId, santriId, amount);
    }

    @Override
    public String eventType() {
        return "pesantren.wallet.spent";
    }
}
