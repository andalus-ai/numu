package tech.kayys.numou.commerce.marketplace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.commerce.marketplace.application.port.IdempotentMarketplaceOrderRepository;
import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderAuthorization;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IdempotentMarketplaceOrderCreationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    @Test
    void retriesReturnTheSameOrderAndDifferentRequestsConflict() {
        Store store = new Store();
        var service = new IdempotentMarketplaceOrderCreationService(store, new BuyerAuthorization(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        MarketplaceOrder first = service.create("tenant-a", "buyer-actor", "buyer-a", "checkout-1",
                List.of(line(new BigDecimal("2.0"))), "IDR");
        MarketplaceOrder retry = service.create("tenant-a", "buyer-actor", "buyer-a", "checkout-1",
                List.of(line(new BigDecimal("2.00"))), "IDR");

        assertEquals(first.id(), retry.id());
        assertEquals(1, store.orders.size());
        assertThrows(MarketplaceOrderIdempotencyConflictException.class, () ->
                service.create("tenant-a", "buyer-actor", "buyer-a", "checkout-1",
                        List.of(line(new BigDecimal("3"))), "IDR"));
    }

    @Test
    void fingerprintIsStableForEquivalentDecimalScalesAndChangesForCommercialChanges() {
        var two = List.of(line(new BigDecimal("2.0")));
        var equivalent = List.of(line(new BigDecimal("2.000")));
        var three = List.of(line(new BigDecimal("3.0")));

        assertEquals(MarketplaceOrderRequestFingerprint.calculate("tenant-a", "buyer-a", "IDR", two),
                MarketplaceOrderRequestFingerprint.calculate("tenant-a", "buyer-a", "IDR", equivalent));
        assertNotEquals(MarketplaceOrderRequestFingerprint.calculate("tenant-a", "buyer-a", "IDR", two),
                MarketplaceOrderRequestFingerprint.calculate("tenant-a", "buyer-a", "IDR", three));
    }

    private static MarketplaceOrderLine line(BigDecimal quantity) {
        return new MarketplaceOrderLine("line-1", "produce-1", "offer-1", "seller-1",
                "Fresh produce", quantity, "kg", new BigDecimal("12500.00"), "IDR");
    }

    private static final class BuyerAuthorization implements MarketplaceOrderAuthorization {
        @Override public void requireBuyer(String tenantId, String actorId, MarketplaceOrder order) {
            if (!"tenant-a".equals(tenantId) || !"buyer-actor".equals(actorId)
                    || !"buyer-a".equals(order.buyerId())) throw new SecurityException("not authorized");
        }
        @Override public void requireSeller(String tenantId, String actorId, String sellerId, MarketplaceOrder order) {
            throw new UnsupportedOperationException();
        }
        @Override public void requireFulfillmentOperator(String tenantId, String actorId, MarketplaceOrder order) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Store implements IdempotentMarketplaceOrderRepository {
        private final Map<String, MarketplaceOrder> orders = new HashMap<>();
        private final Map<String, String> fingerprints = new HashMap<>();

        @Override public MarketplaceOrder createOrGet(String tenantId, String key, String fingerprint,
                                                       MarketplaceOrder proposed) {
            String scope = tenantId + ":" + key;
            MarketplaceOrder existing = orders.get(scope);
            if (existing != null) {
                if (!fingerprints.get(scope).equals(fingerprint)) {
                    throw new MarketplaceOrderIdempotencyConflictException("conflicting request");
                }
                return existing;
            }
            orders.put(scope, proposed);
            fingerprints.put(scope, fingerprint);
            return proposed;
        }
    }
}
