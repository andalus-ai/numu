package tech.kayys.numou.asset.application.meter;
import java.time.Instant;
import java.util.UUID;

public record GetMeterUsageQuery(String tenantId, UUID meterId, Instant from, Instant to) {}
