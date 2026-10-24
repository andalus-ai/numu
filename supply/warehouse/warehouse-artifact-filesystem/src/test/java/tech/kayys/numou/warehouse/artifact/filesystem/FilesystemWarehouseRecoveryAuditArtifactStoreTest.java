package tech.kayys.numou.warehouse.artifact.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class FilesystemWarehouseRecoveryAuditArtifactStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void appendsChunksAndRetryDoesNotDuplicateBytes() throws Exception {
        var store = new FilesystemWarehouseRecoveryAuditArtifactStore(temporaryDirectory);
        byte[] first = "header\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "row-1\n".getBytes(StandardCharsets.UTF_8);

        var one = store.appendChunk("tenant-a", "export-1", "chunk-1", 0, first);
        var retry = store.appendChunk("tenant-a", "export-1", "chunk-1", 0, first);
        assertEquals(one.byteSize(), retry.byteSize());
        assertEquals(one.sha256Hex(), retry.sha256Hex());

        var two = store.appendChunk("tenant-a", "export-1", "chunk-2", first.length, second);
        assertEquals(first.length + second.length, two.byteSize());
        assertEquals("header\nrow-1\n",
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
                "header\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("header\n",
                new String(store.open("tenant-a", "export-1").readAllBytes(), StandardCharsets.UTF_8));
        assertEquals(7, snapshot.byteSize());
    }

    @Test
    void reconstructsReceiptAfterArtifactWasWrittenButReceiptWasNotCommitted() throws Exception {
        byte[] chunk = "durable-row\n".getBytes(StandardCharsets.UTF_8);
        Path exportDirectory = temporaryDirectory.resolve(hash("tenant-a")).resolve(hash("export-crash"));
        Files.createDirectories(exportDirectory.resolve("chunks"));
        // Simulate a process crash after the artifact bytes were forced but before the receipt rename.
        Files.write(exportDirectory.resolve("audit.csv"), chunk);

        var recoveredStore = new FilesystemWarehouseRecoveryAuditArtifactStore(temporaryDirectory);
        var recovered = recoveredStore.appendChunk("tenant-a", "export-crash", "chunk-1", 0, chunk);
        assertEquals(chunk.length, recovered.byteSize());
        assertEquals(new String(chunk, StandardCharsets.UTF_8),
                new String(recoveredStore.open("tenant-a", "export-crash").readAllBytes(), StandardCharsets.UTF_8));

        // A fresh adapter instance must recognize the reconstructed durable receipt as an idempotent retry.
        var restartedStore = new FilesystemWarehouseRecoveryAuditArtifactStore(temporaryDirectory);
        var retry = restartedStore.appendChunk("tenant-a", "export-crash", "chunk-1", 0, chunk);
        assertEquals(recovered.byteSize(), retry.byteSize());
        assertEquals(recovered.sha256Hex(), retry.sha256Hex());
    }

    @Test
    void concurrentRetriesOfSameChunkDoNotDuplicateBytes() throws Exception {
        var store = new FilesystemWarehouseRecoveryAuditArtifactStore(temporaryDirectory);
        byte[] chunk = "one-row\n".getBytes(StandardCharsets.UTF_8);
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<WarehouseSnapshot> write = () -> {
                var snapshot = store.appendChunk("tenant-a", "export-concurrent", "chunk-1", 0, chunk);
                return new WarehouseSnapshot(snapshot.byteSize(), snapshot.sha256Hex());
            };
            var tasks = java.util.stream.IntStream.range(0, 24).<Callable<WarehouseSnapshot>>mapToObj(i -> write).toList();
            var futures = executor.invokeAll(tasks);
            String expectedDigest = store.inspect("tenant-a", "export-concurrent").sha256Hex();
            for (Future<WarehouseSnapshot> future : futures) {
                assertEquals(chunk.length, future.get().byteSize());
                assertEquals(expectedDigest, future.get().sha256Hex());
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals("one-row\n",
                new String(store.open("tenant-a", "export-concurrent").readAllBytes(), StandardCharsets.UTF_8));
    }

    private record WarehouseSnapshot(long byteSize, String sha256Hex) {}

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
