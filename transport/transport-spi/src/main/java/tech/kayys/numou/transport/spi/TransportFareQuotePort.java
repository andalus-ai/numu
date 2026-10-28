package tech.kayys.numou.transport.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** Trusted server-side fare quote contract; checkout clients never choose the amount. */
public interface TransportFareQuotePort {
    Quote quote(QuoteRequest request);

    record QuoteRequest(String tenantId, UUID departureId, int fromSequence,
                        int toSequence, int passengerCount) {
        public QuoteRequest {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            Objects.requireNonNull(departureId, "departureId");
            if (fromSequence < 1 || toSequence <= fromSequence) throw new IllegalArgumentException("invalid segment");
            if (passengerCount < 1) throw new IllegalArgumentException("passengerCount must be positive");
        }
    }

    record Quote(String quoteId, BigDecimal amount, String currency, Instant expiresAt) {
        public Quote {
            if (quoteId == null || quoteId.isBlank()) throw new IllegalArgumentException("quoteId is required");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
            if (amount.stripTrailingZeros().scale() > 4 || amount.precision() > 19) {
                throw new IllegalArgumentException("amount exceeds supported precision (19 digits, 4 decimal places)");
            }
            if (currency == null || !currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("currency must be uppercase ISO-4217");
            }
            Currency.getInstance(currency);
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
