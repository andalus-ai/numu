package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.meter.MeterReading;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Outbound port for meter readings (ASSET-21 §21.18). */
public interface MeterReadingRepository {

    CompletionStage<MeterReading> save(String tenantId, MeterReading reading);

    CompletionStage<Optional<MeterReading>> findBySourceRef(String tenantId, String source, String sourceRef);

    CompletionStage<List<MeterReading>> findByMeterId(String tenantId, UUID meterId);

    CompletionStage<Optional<MeterReading>> findLatest(String tenantId, UUID meterId);

    CompletionStage<Optional<MeterReading>> findPrevious(String tenantId, UUID meterId, Instant recordedAt);

    CompletionStage<Optional<MeterReading>> findNext(String tenantId, UUID meterId, Instant recordedAt);

    /** Returns {@code true} when a reading with the same idempotency key already exists. */
    default CompletionStage<Boolean> existsBySourceRef(String tenantId, String source, String sourceRef) {
        return findBySourceRef(tenantId, source, sourceRef).thenApply(Optional::isPresent);
    }
}
