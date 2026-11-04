package tech.kayys.numou.pos.adapter.persistence;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.pos.domain.CheckoutSession;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PosPostgresTestResource.class)
class PostgresCheckoutRepositoryTest {
    @Inject PostgresCheckoutRepository repository;
    @Inject DataSource dataSource;

    @BeforeEach void clean() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM pos_checkout_session");
        }
    }

    @Test void persistsCheckoutAndEnforcesTenantScopedReads() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        UUID id = UUID.randomUUID(), sale = UUID.randomUUID();
        CheckoutSession checkout = CheckoutSession.start(id, "tenant-a", sale,
                Money.of(new BigDecimal("125.00"), Currency.of("IDR")), now);
        assertEquals(checkout, repository.create(checkout));
        assertEquals(checkout, repository.find("tenant-a", id).orElseThrow());
        assertTrue(repository.find("tenant-b", id).isEmpty());
    }

    @Test void optimisticVersionRejectsStaleCheckoutUpdate() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        UUID id = UUID.randomUUID();
        CheckoutSession checkout = CheckoutSession.start(id, "tenant-a", UUID.randomUUID(),
                Money.of(new BigDecimal("125.00"), "IDR"), now);
        repository.create(checkout);
        CheckoutSession reserved = checkout.reserveStock("reservation-1", now.plusSeconds(1));
        assertEquals(1, repository.update(reserved, 0).version());
        assertThrows(PostgresCheckoutRepository.OptimisticCheckoutConflictException.class,
                () -> repository.update(reserved, 0));
    }
}
