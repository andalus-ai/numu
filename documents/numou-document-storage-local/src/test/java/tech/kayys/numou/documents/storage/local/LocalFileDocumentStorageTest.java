package tech.kayys.numou.documents.storage.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.numou.documents.storage.spi.StorageKey;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileDocumentStorageTest {
    @TempDir Path tempDir;

    @Test
    void roundTripsTenantScopedContentAndChecksum() throws Exception {
        LocalFileDocumentStorage storage = new LocalFileDocumentStorage(tempDir.resolve("docs"));
        byte[] content = "hello document".getBytes(StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        StorageKey key = new StorageKey("tenant-a", "folder/doc-1");

        var stored = storage.put(key, new ByteArrayInputStream(content), content.length, "text/plain", sha);
        assertEquals(sha, stored.sha256().orElseThrow());
        try (var object = storage.get(key).orElseThrow()) {
            assertEquals("hello document", new String(object.content().readAllBytes(), StandardCharsets.UTF_8));
        }
        assertTrue(storage.head(key).isPresent());
        assertTrue(storage.delete(key));
        assertTrue(storage.head(key).isEmpty());
    }

    @Test
    void rejectsIncorrectDeclaredLength() throws Exception {
        LocalFileDocumentStorage storage = new LocalFileDocumentStorage(tempDir.resolve("docs"));
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> storage.put(new StorageKey("tenant-a", "doc"),
                new ByteArrayInputStream(content), 4, "text/plain", null));
    }

    @Test
    void rejectsChecksumMismatch() throws Exception {
        LocalFileDocumentStorage storage = new LocalFileDocumentStorage(tempDir.resolve("docs"));
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> storage.put(new StorageKey("tenant-a", "doc"),
                new ByteArrayInputStream(content), content.length, "text/plain", "bad"));
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> new StorageKey("tenant-a", "../secret"));
        assertThrows(IllegalArgumentException.class, () -> new StorageKey("tenant-a", "/secret"));
    }
}
