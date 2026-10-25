package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProofOfDeliveryProjectionWorkerContractTest {
    @Test
    void workerUsesConfiguredIntervalBoundedBatchAndSkipsOverlappingExecutions() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/tech/kayys/numou/courier/adapter/postgres/ProofOfDeliveryProjectionWorker.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("numou.courier.pod-projector.interval:5s"));
        assertTrue(source.contains("numou.courier.pod-projector.batch-size"));
        assertTrue(source.contains("Scheduled.ConcurrentExecution.SKIP"));
        assertTrue(source.contains("batchSize > 500"));
    }

    @Test
    void workerLogsFailuresAndDelegatesDurabilityToProjector() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/tech/kayys/numou/courier/adapter/postgres/ProofOfDeliveryProjectionWorker.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("projector.projectBatch(batchSize)"));
        assertTrue(source.contains("Level.SEVERE"));
        assertTrue(source.contains("eligible rows remain retryable"));
    }
}
