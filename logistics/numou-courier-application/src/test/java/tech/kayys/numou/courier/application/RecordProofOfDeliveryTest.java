package tech.kayys.numou.courier.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.application.port.ProofOfDeliveryStore;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordProofOfDeliveryTest {
    private final ProofOfDeliveryStore store = mock(ProofOfDeliveryStore.class);
    private final RecordProofOfDelivery useCase = new RecordProofOfDelivery(store);
    private final Instant occurred = Instant.parse("2026-10-24T10:00:00Z");

    @Test
    void recordsValidatedEvidenceAndNormalizesDigest() {
        when(store.record(any())).thenReturn(CompletableFuture.completedFuture(
                new ProofOfDeliveryStore.Result(ProofOfDeliveryStore.Outcome.ACCEPTED, UUID.randomUUID())));
        var result = useCase.execute(request("A".repeat(64), occurred)).toCompletableFuture().join();
        assertEquals(ProofOfDeliveryStore.Outcome.ACCEPTED, result.outcome());
        var captor = org.mockito.ArgumentCaptor.forClass(ProofOfDeliveryStore.Command.class);
        verify(store).record(captor.capture());
        assertEquals("a".repeat(64), captor.getValue().evidenceSha256());
    }

    @Test
    void rejectsInvalidDigestBeforePersistence() {
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> useCase.execute(request("not-a-digest", occurred)).toCompletableFuture().join());
        verifyNoInteractions(store);
    }

    @Test
    void rejectsDeliveryTimeAfterCommandOccurrence() {
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> useCase.execute(request("a".repeat(64), occurred.minusSeconds(1)))
                        .toCompletableFuture().join());
        verifyNoInteractions(store);
    }

    private RecordProofOfDelivery.Request request(String digest, Instant occurredAt) {
        return new RecordProofOfDelivery.Request(TenantId.of(UUID.randomUUID()), UUID.randomUUID(),
                "shipment-1", "attempt-1", occurred, "Recipient", "evidence://tenant/object/1",
                digest, "courier-1", "handheld", "pod-key-1", "fingerprint-1", occurredAt);
    }
}
