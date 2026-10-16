package tech.kayys.syirkah.document.adapter.outbound.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.syirkah.document.application.port.DocumentStoragePort;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalDocumentStorageTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @TempDir
    Path temp;

    @Test
    void stores_only_authorized_uploads_and_reverifies_content() throws Exception {
        var content = "confidential document".getBytes(StandardCharsets.UTF_8);
        var checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        var storage = new LocalDocumentStorage(
                temp.resolve("objects"),
                URI.create("https://documents.example.test"),
                1024,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        var tenantId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var target = createTarget(storage, tenantId, sessionId, content.length, checksum);
        var token = target.requiredHeaders().get("X-Upload-Token");

        assertNull(URI.create(target.uploadUrl()).getRawQuery());
        assertThrows(SecurityException.class, () -> storage.acceptUpload(
                tenantId.toString(), sessionId.toString(), "wrong-token", "application/pdf",
                new ByteArrayInputStream(content)
        ));

        storage.acceptUpload(
                tenantId.toString(), sessionId.toString(), token, "application/pdf",
                new ByteArrayInputStream(content)
        );
        var stored = storage.inspect(target.storageKey()).await().indefinitely();

        assertEquals(content.length, stored.size());
        assertEquals("application/pdf", stored.contentType());
        assertEquals(checksum, stored.sha256());
        assertThrows(IllegalStateException.class, () -> storage.acceptUpload(
                tenantId.toString(), sessionId.toString(), token, "application/pdf",
                new ByteArrayInputStream(content)
        ));
    }

    @Test
    void rejects_mismatched_size_and_storage_keys_outside_tenant_namespace() {
        var content = "too much".getBytes(StandardCharsets.UTF_8);
        var storage = new LocalDocumentStorage(
                temp.resolve("objects"),
                URI.create("http://localhost:8083"),
                1024,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        var tenantId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var target = createTarget(storage, tenantId, sessionId, content.length - 1, null);

        assertThrows(IllegalArgumentException.class, () -> storage.acceptUpload(
                tenantId.toString(), sessionId.toString(),
                target.requiredHeaders().get("X-Upload-Token"), "application/pdf",
                new ByteArrayInputStream(content)
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.inspect("../../outside").await().indefinitely());
    }

    private static DocumentStoragePort.UploadTarget createTarget(
            LocalDocumentStorage storage,
            UUID tenantId,
            UUID sessionId,
            long size,
            String checksum
    ) {
        return storage.createUploadTarget(new DocumentStoragePort.UploadTargetRequest(
                tenantId.toString(),
                sessionId.toString(),
                "ignored-name.pdf",
                "application/pdf",
                size,
                checksum,
                NOW.plusSeconds(120)
        )).await().indefinitely();
    }
}
