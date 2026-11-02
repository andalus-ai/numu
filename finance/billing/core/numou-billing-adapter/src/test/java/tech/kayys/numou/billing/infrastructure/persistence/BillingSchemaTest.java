package tech.kayys.numou.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tech.kayys.numou.billing.infrastructure.persistence.entity.BillingScheduleEntity;
import tech.kayys.numou.billing.infrastructure.persistence.entity.UsageRecordEntity;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Applies the migrations to an empty schema of a real PostgreSQL and checks them against the entities: every mapped
 * column exists, and no column the entities do not map is required (an insert would fail on it). Needs
 * BILLING_PG_URL, BILLING_PG_USER and BILLING_PG_PASSWORD.
 */
@EnabledIfEnvironmentVariable(named = "BILLING_PG_URL", matches = ".+")
class BillingSchemaTest {

    static final List<String> MIGRATIONS = List.of("V1__billing_schema.sql");

    static Set<String> mappedColumns(Class<?> entity) {
        Set<String> columns = new TreeSet<>();
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                Column col = f.getAnnotation(Column.class);
                if (col != null) {
                    assertTrue(columns.add(col.name()), "column mapped twice in " + entity.getSimpleName() + ": "
                            + col.name());
                }
            }
        }
        return columns;
    }

    @Test
    void migrationsMatchTheEntities() throws Exception {
        String schema = "billing_it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(System.getenv("BILLING_PG_URL"),
                System.getenv("BILLING_PG_USER"), System.getenv("BILLING_PG_PASSWORD"));
             Statement st = c.createStatement()) {
            st.execute("create schema " + schema);
            try {
                st.execute("set search_path to " + schema);
                for (String script : MIGRATIONS) {
                    try (InputStream in = BillingSchemaTest.class.getResourceAsStream("/db/migration/" + script)) {
                        assertNotNull(in, script);
                        st.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                for (Class<?> entity : List.of(BillingScheduleEntity.class, UsageRecordEntity.class)) {
                    String table = entity.getAnnotation(Table.class).name();
                    Set<String> mapped = mappedColumns(entity);
                    Set<String> existing = new TreeSet<>();
                    Set<String> requiredWithoutDefault = new TreeSet<>();
                    try (ResultSet rs = st.executeQuery("select column_name, is_nullable, column_default "
                            + "from information_schema.columns where table_schema = '" + schema
                            + "' and table_name = '" + table + "'")) {
                        while (rs.next()) {
                            existing.add(rs.getString(1));
                            if ("NO".equals(rs.getString(2)) && rs.getString(3) == null) {
                                requiredWithoutDefault.add(rs.getString(1));
                            }
                        }
                    }
                    assertFalse(existing.isEmpty(), "table " + table + " was not created");
                    Set<String> missing = new TreeSet<>(mapped);
                    missing.removeAll(existing);
                    assertTrue(missing.isEmpty(), table + " lacks mapped columns " + missing);
                    Set<String> unmappedRequired = new TreeSet<>(requiredWithoutDefault);
                    unmappedRequired.removeAll(mapped);
                    assertTrue(unmappedRequired.isEmpty(),
                            table + " has required columns the entity does not map: " + unmappedRequired);
                }
            } finally {
                st.execute("drop schema " + schema + " cascade");
            }
        }
    }
}
