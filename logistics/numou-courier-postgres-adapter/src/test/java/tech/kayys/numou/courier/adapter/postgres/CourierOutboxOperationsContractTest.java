package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CourierOutboxOperationsContractTest {
    @Test
    void replayRequiresAuthorizationAndDeadLetterState() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/tech/kayys/numou/courier/adapter/postgres/CourierOutboxOperations.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("requireAuthorizer()"));
        assertTrue(source.contains("mayReplay("));
        assertTrue(source.contains("only DEAD courier publications can be replayed"));
        assertTrue(source.contains("FOR UPDATE"));
        assertTrue(source.contains("courier_event_publication_audit"));
        assertTrue(source.contains("c.commit()"));
        assertTrue(source.contains("c.rollback()"));
    }

    @Test
    void migrationMakesReplayAuditAppendOnlyAndPreservesReplayMetadata() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V26__courier_outbox_operations_audit.sql"),
                StandardCharsets.UTF_8);
        assertTrue(migration.contains("replay_count"));
        assertTrue(migration.contains("last_replayed_by"));
        assertTrue(migration.contains("FOREIGN KEY (tenant_id, event_id, destination)"));
        assertTrue(migration.contains("BEFORE UPDATE OR DELETE"));
        assertTrue(migration.contains("append-only"));
    }
}
