package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcHubRepositoryTenantBoundaryTest {
    @Test
    void refusesToPersistAggregateUnderAnotherTenantBeforeOpeningConnection() {
        TenantId owner = TenantId.generate();
        TenantId caller = TenantId.generate();
        Hub hub = Hub.create(HubId.generate(), owner, OrganizationId.generate(), OrganizationUnitId.generate(),
                "JKT-01", "Jakarta Hub", HubType.ORIGIN_HUB,
                new HubAddress("Street 1", null, "Jakarta", null, null, "ID", -6.2, 106.8),
                ZoneId.of("Asia/Jakarta"), Instant.parse("2026-10-24T00:00:00Z"));

        JdbcHubRepository repository = new JdbcHubRepository(null);
        CompletionException failure = assertThrows(CompletionException.class,
                () -> repository.save(caller, hub).toCompletableFuture().join());

        assertInstanceOf(SecurityException.class, failure.getCause());
    }
}
