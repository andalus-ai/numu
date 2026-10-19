package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

/** Domain events raised by the {@link Syirkah} aggregate. */
public final class SyirkahEvents {

    private SyirkahEvents() {
    }

    public record SyirkahOpened(UUID eventId, Instant occurredAt, SyirkahId syirkahId, SyirkahType type,
                                int contractVersion) implements DomainEvent {
        @Override
        public String eventType() {
            return "syirkah.opened";
        }
    }

    public record PeriodClosed(UUID eventId, Instant occurredAt, SyirkahId syirkahId, CloseId closeId,
                               Money netResult, ProfitDisposition disposition, String hash) implements DomainEvent {
        @Override
        public String eventType() {
            return "syirkah.period-closed";
        }
    }

    public record ProfitDistributed(UUID eventId, Instant occurredAt, SyirkahId syirkahId, PartnerId partner,
                                    Money amount, String reference) implements DomainEvent {
        @Override
        public String eventType() {
            return "syirkah.profit-distributed";
        }
    }

    public record LiabilitySettled(UUID eventId, Instant occurredAt, SyirkahId syirkahId, PartnerId partner,
                                   Money amount, String reference) implements DomainEvent {
        @Override
        public String eventType() {
            return "syirkah.liability-settled";
        }
    }

    public record ContractRevised(UUID eventId, Instant occurredAt, SyirkahId syirkahId, int newVersion)
            implements DomainEvent {
        @Override
        public String eventType() {
            return "syirkah.contract-revised";
        }
    }

    public record CloseCorrected(UUID eventId, Instant occurredAt, SyirkahId syirkahId, CloseId reversed,
                                 CloseId replacement, Money delta) implements DomainEvent {
        @Override
        public String eventType() {
            return "syirkah.close-corrected";
        }
    }

    public record LiquidationCompleted(UUID eventId, Instant occurredAt, SyirkahId syirkahId, Money netResult)
            implements DomainEvent {
        @Override
        public String eventType() {
            return "syirkah.liquidation-completed";
        }
    }
}
