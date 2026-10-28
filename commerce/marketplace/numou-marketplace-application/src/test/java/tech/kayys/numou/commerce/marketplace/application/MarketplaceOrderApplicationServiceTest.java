package tech.kayys.numou.commerce.marketplace.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderAuthorization;
import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderRepository;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceOrderApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    @Test
    void createSubmitAndSellerAcceptUseTenantScopedPersistence() {
        InMemoryOrders orders = new InMemoryOrders();
        MarketplaceOrderApplicationService service = service(orders, new AllowAll());
        MarketplaceOrder created = service.create("tenant-a", "buyer-actor", "buyer-a", List.of(line()), "IDR");

        assertEquals(MarketplaceOrderStatus.CREATED, created.status());
        assertEquals(MarketplaceOrderStatus.SUBMITTED,
                service.submit("tenant-a", "buyer-actor", created.id()).status());
        assertEquals(MarketplaceOrderStatus.SELLER_ACCEPTED,
                service.sellerAccept("tenant-a", "seller-actor", "seller-1", created.id()).status());
        assertTrue(orders.findByTenantAndId("tenant-a", created.id()).isPresent());
        assertTrue(orders.findByTenantAndId("tenant-b", created.id()).isEmpty());
    }

    @Test
    void tenantMismatchDoesNotRevealOrder() {
        InMemoryOrders orders = new InMemoryOrders();
        MarketplaceOrderApplicationService service = service(orders, new AllowAll());
        MarketplaceOrder order = service.create("tenant-a", "buyer-actor", "buyer-a", List.of(line()), "IDR");

        assertThrows(IllegalArgumentException.class,
                () -> service.submit("tenant-b", "buyer-actor", order.id()));
        assertEquals(MarketplaceOrderStatus.CREATED,
                orders.findByTenantAndId("tenant-a", order.id()).orElseThrow().status());
    }

    @Test
    void concurrentStatusChangeIsReportedRatherThanOverwritten() {
        InMemoryOrders orders = new InMemoryOrders();
        MarketplaceOrderApplicationService service = service(orders, new AllowAll());
        MarketplaceOrder order = service.create("tenant-a", "buyer-actor", "buyer-a", List.of(line()), "IDR");
        orders.forceStatus(order.id(), order.submit(NOW));

        assertThrows(MarketplaceOrderConcurrentModificationException.class,
                () -> service.submit("tenant-a", "buyer-actor", order.id()));
    }

    @Test
    void authorizationIsCheckedBeforeMutation() {
        InMemoryOrders orders = new InMemoryOrders();
        MarketplaceOrderAuthorization deny = new AllowAll() {
            @Override public void requireBuyer(String tenantId, String actorId, MarketplaceOrder order) {
                throw new SecurityException("denied");
            }
        };
        MarketplaceOrderApplicationService service = service(orders, deny);

        assertThrows(SecurityException.class,
                () -> service.create("tenant-a", "attacker", "buyer-a", List.of(line()), "IDR"));
        assertTrue(orders.data.isEmpty());
    }

    @Test
    void mixedSellerCartMustBeSplitBeforeCreatingSellerScopedOrders() {
        InMemoryOrders orders = new InMemoryOrders();
        MarketplaceOrderApplicationService service = service(orders, new AllowAll());
        MarketplaceOrderLine otherSeller = new MarketplaceOrderLine("line-2", "produce-2", "offer-2",
                "seller-2", "Eggs", BigDecimal.ONE, "dozen", new BigDecimal("30000"), "IDR");

        assertThrows(IllegalArgumentException.class,
                () -> service.create("tenant-a", "buyer-actor", "buyer-a", List.of(line(), otherSeller), "IDR"));
        assertTrue(orders.data.isEmpty());
    }

    private static MarketplaceOrderApplicationService service(InMemoryOrders orders,
                                                               MarketplaceOrderAuthorization auth) {
        return new MarketplaceOrderApplicationService(
                orders, auth, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MarketplaceOrderLine line() {
        return new MarketplaceOrderLine("line-1", "produce-1", "offer-1", "seller-1",
                "Fresh produce", new BigDecimal("2"), "kg", new BigDecimal("12500"), "IDR");
    }

    private static class AllowAll implements MarketplaceOrderAuthorization {
        @Override public void requireBuyer(String tenantId, String actorId, MarketplaceOrder order) {
            if (!tenantId.equals(order.tenantId()) || actorId == null || actorId.isBlank()
                    || !order.buyerId().equals("buyer-a") || !actorId.equals("buyer-actor")) {
                throw new SecurityException("buyer is not authorized");
            }
        }
        @Override public void requireSeller(String tenantId, String actorId, String sellerId, MarketplaceOrder order) {
            if (!tenantId.equals(order.tenantId()) || !"seller-actor".equals(actorId)
                    || !order.lines().stream().allMatch(line -> sellerId.equals(line.sellerId()))) {
                throw new SecurityException("seller is not authorized");
            }
        }
        @Override public void requireFulfillmentOperator(String tenantId, String actorId, MarketplaceOrder order) {
            if (!tenantId.equals(order.tenantId()) || actorId == null || actorId.isBlank()) {
                throw new SecurityException("operator is not authorized");
            }
        }
    }

    private static final class InMemoryOrders implements MarketplaceOrderRepository {
        private final Map<UUID, MarketplaceOrder> data = new HashMap<>();

        @Override public void create(MarketplaceOrder order) {
            if (data.putIfAbsent(order.id(), order) != null) throw new IllegalStateException("duplicate order");
        }
        @Override public Optional<MarketplaceOrder> findByTenantAndId(String tenantId, UUID orderId) {
            MarketplaceOrder order = data.get(orderId);
            return order != null && order.tenantId().equals(tenantId) ? Optional.of(order) : Optional.empty();
        }
        @Override public boolean replaceIfCurrentStatus(String tenantId, UUID orderId,
                                                        MarketplaceOrderStatus expected,
                                                        MarketplaceOrder replacement) {
            MarketplaceOrder current = data.get(orderId);
            if (current == null || !current.tenantId().equals(tenantId)
                    || current.status() != expected || !replacement.id().equals(orderId)
                    || !replacement.tenantId().equals(tenantId)) return false;
            data.put(orderId, replacement);
            return true;
        }
        void forceStatus(UUID id, MarketplaceOrder order) { data.put(id, order); }
    }
}