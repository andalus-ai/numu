package tech.kayys.numou.asset.application.meter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeterSnapshot(UUID meterId, UUID assetId, String type, String unit, BigDecimal latestValue, Instant latestRecordedAt) {}
