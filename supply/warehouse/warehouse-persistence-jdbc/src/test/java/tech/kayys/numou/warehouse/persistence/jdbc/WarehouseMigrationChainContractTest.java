package tech.kayys.numou.warehouse.persistence.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Guards against duplicate Flyway versions and missing warehouse schema steps. */
class WarehouseMigrationChainContractTest {
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([0-9]+)__.*\\.sql$");

    @Test
    void migrationVersionsAreUniqueAndWarehouseChainIsContiguous() throws IOException, URISyntaxException {
        var resource = getClass().getClassLoader().getResource("db/migration");
        assertNotNull(resource, "db/migration test resource directory must exist");
        Path migrationDirectory = Path.of(resource.toURI());
        Map<Integer, String> versions = new HashMap<>();

        try (Stream<Path> files = Files.list(migrationDirectory)) {
            files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .forEach(name -> {
                        Matcher matcher = VERSIONED_MIGRATION.matcher(name);
                        assertTrue(matcher.matches(), "Invalid Flyway migration filename: " + name);
                        int version = Integer.parseInt(matcher.group(1));
                        String previous = versions.putIfAbsent(version, name);
                        assertNull(previous, "Duplicate Flyway version V" + version + ": "
                                + previous + " and " + name);
                    });
        }

        for (int version = 1; version <= 6; version++ {
            assertTrue(versions.containsKey(version), "Missing warehouse migration V" + version);
        }
        assertEquals("V2__warehouse_transactional_execution.sql", versions.get(2),
                "V2 must remain the transactional execution migration");
        assertEquals("V5__warehouse_retry_schedule.sql", versions.get(5),
                "Retry scheduling must follow V3 leases and V4 replay-payload migrations");
        assertEquals("V6__warehouse_recovery_operator_audit.sql", versions.get(6),
                "Operator audit must be introduced as a forward-only migration");
    }
}
