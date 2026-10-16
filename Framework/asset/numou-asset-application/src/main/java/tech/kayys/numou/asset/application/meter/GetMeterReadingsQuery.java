package tech.kayys.numou.asset.application.meter;
import java.util.UUID;

public record GetMeterReadingsQuery(String tenantId, UUID meterId) {}
