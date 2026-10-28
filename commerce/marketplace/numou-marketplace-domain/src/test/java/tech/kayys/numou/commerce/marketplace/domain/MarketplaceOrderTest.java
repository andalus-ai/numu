package tech.kayys.numou.commerce.marketplace.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceOrderTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private MarketplaceOrder order() {
        return MarketplaceOrder.create(UUID.randomUUID(), "tenant-1", "buyer-1",
                List.of(new MarketplaceOrderLine("line-1", "product-1", "offer-1", "seller-1",
                        "Fresh tomatoes", new BigDecimal("2.5"), "kg", new BigDecimal("18000.00"), "IDR")),
                "IDR", NOW);
    }

    @Test
    void followsCommercialLifecycleWithoutClaimingPaymentOrStockSuccess() {
        MarketplaceOrder order = order().submit(NOW.plusSeconds(1)).sellerAccept(NOW.plusSeconds(2));
        assertEquals(MarketplaceOrderStatus.SELLER_ACCEPTED, order.status());
        assertEquals("tenant-1", order.tenantId());
        assertEquals(new BigDecimal("18000.00"), order.lines().get(0).unitPrice());

        MarketplaceOrder fulfillment = order.requestFulfillment(NOW.plusSeconds(3));
        assertEquals(MarketplaceOrderStatus.FULFILLMENT_REQUESTED, fulfillment.status());
        assertThrows(IllegalStateException.class, () -> fulfillment.sellerAccept(NOW.plusSeconds(4)));
    }

    @Test
    void rejectsInvalidQuantityAndMixedCurrencies() {
        assertThrows(IllegalArgumentException.class, () -> new MarketplaceOrderLine(
                "line-1", "product-1", "offer-1", "seller-1", "Produce",
                BigDecimal.ZERO, "kg", BigDecimal.ONE, "IDR"));
        MarketplaceOrderLine usd = new MarketplaceOrderLine("line-2", "p2", "o2", "s2", "Produce",
                BigDecimal.ONE, "kg", BigDecimal.ONE, "USD");
        assertThrows(IllegalArgumentException.class, () -> MarketplaceOrder.create(
                UUID.randomUUID(), "tenant-1", "buyer-1", List.of(usd), "IDR", NOW));
    }

    @Test
    void rejectsLinesFromDifferentSellersInOneOrder() {
        MarketplaceOrderLine otherSeller = new MarketplaceOrderLine("line-2", "product-2", "offer-2",
                "seller-2", "Eggs", BigDecimal.ONE, "dozen", new BigDecimal("30000"), "IDR");
        assertThrows(IllegalArgumentException.class, () -> MarketplaceOrder.create(
                UUID.randomUUID(), "tenant-1", "buyer-1",
                List.of(order().lines().get(0), otherSeller), "IDR", NOW));
    }

    @Test
    void cancellationRequiresReasonAndCannotCancelAfterFulfillmentRequested() {
        assertThrows(IllegalArgumentException.class, () -> order().cancel(" ", NOW.plusSeconds(1)));
        MarketplaceOrder order = order().submit(NOW.plusSeconds(1)).sellerAccept(NOW.plusSeconds(2))
                .requestFulfillment(NOW.plusSeconds(3));
        assertThrows(IllegalStateException.class, () -> order.cancel("buyer request", NOW.plusSeconds(4)));
    }

    @Test
    void snapshotsAreImmutableAndTimestampsCannotMoveBackwards() {
        MarketplaceOrder order = order();
        assertThrows(UnsupportedOperationException.class, () -> order.lines().clear());
        assertThrows(IllegalArgumentException.class, () -> order.submit(NOW.minusSeconds(1)));
    }
}
