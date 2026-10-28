package tech.kayys.numou.affiliate.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateCommandContextTest {
    @Test
    void contextRequiresTenantActorIdempotencyAndCorrelation() {
        assertThrows(IllegalArgumentException.class,
                () -> new AffiliateCommandContext(" ", "operator", "key", "corr"));
        assertThrows(IllegalArgumentException.class,
                () -> new AffiliateCommandContext("tenant", " ", "key", "corr"));
        assertThrows(IllegalArgumentException.class,
                () -> new AffiliateCommandContext("tenant", "operator", "", "corr"));
        assertEquals("tenant-a", new AffiliateCommandContext(" tenant-a ", "operator", "key", "corr").tenantId());
    }
}
