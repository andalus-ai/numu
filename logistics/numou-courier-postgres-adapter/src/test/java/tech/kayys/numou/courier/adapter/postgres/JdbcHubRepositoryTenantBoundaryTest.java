package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class JdbcHubRepositoryTenantBoundaryTest {
    @Test
    void refusesToPersistAggregateUnderAnotherTenantBeforeOpeningConnection() {
        TenantId owner = TenantId.generate();
        TenantId caller = TenantId.generate();
        Hub hub = hub(owner);

        JdbcHubRepository repository = new JdbcHubRepository(null);
        CompletionException failure = assertThrows(CompletionException.class,
                () -> repository.save(caller, hub).toCompletableFuture().join());

        assertInstanceOf(SecurityException.class, failure.getCause());
    }

    @Test
    void lookupAndDeleteAlwaysIncludeTenantPredicate() {
        TenantId tenant = TenantId.generate();
        HubId hubId = HubId.generate();
        List<String> statements = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        DataSource dataSource = dataSource(statements, parameters);
        JdbcHubRepository repository = new JdbcHubRepository(dataSource);

        assertTrue(repository.findById(tenant, hubId).toCompletableFuture().join().isEmpty());
        repository.delete(tenant, hubId).toCompletableFuture().join();

        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("WHERE tenant_id = ? AND id = ?"));
        assertTrue(statements.get(1).contains("DELETE FROM courier_hubs WHERE tenant_id = ? AND id = ?"));
        assertEquals(List.of(tenant.value(), hubId.value(), tenant.value(), hubId.value()), parameters);
    }

    private static Hub hub(TenantId tenant) {
        return Hub.create(HubId.generate(), tenant, OrganizationId.generate(), OrganizationUnitId.generate(),
                "JKT-01", "Jakarta Hub", HubType.ORIGIN_HUB,
                new HubAddress("Street 1", null, "Jakarta", null, null, "ID", -6.2, 106.8),
                ZoneId.of("Asia/Jakarta"), Instant.parse("2026-10-24T00:00:00Z"));
    }

    private static DataSource dataSource(List<String> statements, List<Object> parameters) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) return connection(statements, parameters);
                    if (method.getName().equals("isWrapperFor")) return false;
                    if (method.getName().equals("unwrap")) throw new java.sql.SQLException("not a wrapper");
                    return defaultValue(method.getReturnType());
                });
    }

    private static Connection connection(List<String> statements, List<Object> parameters) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        statements.add((String) args[0]);
                        return statement(parameters);
                    }
                    if (method.getName().equals("isClosed")) return false;
                    return defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement statement(List<Object> parameters) {
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setObject")) {
                        parameters.add(args[1]);
                        return null;
                    }
                    if (method.getName().equals("executeQuery")) return resultSet();
                    if (method.getName().equals("executeUpdate")) return 0;
                    return defaultValue(method.getReturnType());
                });
    }

    private static ResultSet resultSet() {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                    if (method.getName().equals("next")) return false;
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
