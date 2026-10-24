package tech.kayys.numou.warehouse.artifact.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class FilesystemWarehouseRecoveryAuditArtifactStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void appendsChunksAndRetryDoesNotDuplicateBytes() throws Exception {
        var store = new FilesystemWarehouseRecoveryAuditArtifactStore(temporaryDirectory);
        byte[] first = "header\\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "row-1\\n".getBytes(StandardCharsets.UTF_8);

        var one = store.appendChunk("tenant-a", "export-1", "chunk-1", 0, first);
        var retry = store.appendChunk("tenant-a", "export-1", "chunk-1", 0, first);
        assertEquals(one.byteSize(), retry.byteSize());
        assertEquals(one.sha256Hex(), retry.sha256Hex());

        var two = store.appendChunk("tenant-a", "export-1", "chunk-2", first.length, second);
        assertEquals(first.length + second.length, two.byteSize());
        assertEquals("header\\nrow-1\\n",
                new String(store.open("tenant-a", "export-1").readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsReusedChunkIdWithDifferentPayload() throws Exception {
        var store = new FilesystemWarehouseRecoveryAuditArtifactStore(temporaryDirectory);
        store.appendChunk("tenant-a", "export-1", "chunk-1", 0, "abc".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> store.appendChunk("tenant-a", "export-1", "chunk-1", 0,
                "xyz".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsConflictingOffsetAndSeparatesTenants() throws Exception {
        var store = new FilesystemWarehouseRecoveryAuditArtifactStore(temporaryDirectory);
        store.appendChunk("tenant-a", "export-1", "chunk-1", 0, "abc".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> store.appendChunk("tenant-a", "export-1", "chunk-2", 1,
                "def".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> store.inspect("tenant-b", "export-1"));
    }

    @Test
    void repairsPartialAppendWhenReceiptWasNotCommitted() throws Exception {
        var store = new FilesystemWarehouseRecoveryAuditArtifactStore(temporaryDirectory);
        Path exportDirectory = temporaryDirectory.resolve(hash("tenant-a")).resolve(hash("export-1"));
        Files.createDirectories(exportDirectory.resolve("chunks"));
        Files.writeString(exportDirectory.resolve("audit.csv"), "head", StandardCharsets.UTF_8);

        var snapshot = store.appendChunk("tenant-a", "export-1", "chunk-1", 0,
                "header\\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("header\\n",
                new String(store.open("tenant-a", "export-1").readAllBytes(), StandardCharsets.UTF_8));
        assertEquals(7, snapshot.byteSize());
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}