package tech.kayys.syirkah.asset.domain.meter;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
public class MeterDomainTest {
  private AssetMeter meter() {
    return AssetMeter.register(AssetMeterId.generate(), "t1", UUID.randomUUID(), MeterType.ODOMETER, MeterUnit.KM, MeterBehavior.MONOTONIC, "Odo", null);
  }
  @Test void canonicalUnitEnforced() {
    AssetMeter m = meter();
    assertThrows(BusinessRuleViolation.class, () -> MeterReading.record(m, MeterReadingId.generate(), new BigDecimal("10"), MeterUnit.MILE, Instant.now(), Instant.now(), "u", MeterReadingType.NORMAL, null, null));
  }
  @Test void monotonicCorridor() {
    AssetMeter m = meter();
    MeterReading prev = MeterReading.record(m, MeterReadingId.generate(), new BigDecimal("100"), MeterUnit.KM, Instant.parse("2026-01-01T00:00:00Z"), Instant.now(), null, MeterReadingType.NORMAL, null, null);
    MeterReading next = MeterReading.record(m, MeterReadingId.generate(), new BigDecimal("150"), MeterUnit.KM, Instant.parse("2026-01-10T00:00:00Z"), Instant.now(), null, MeterReadingType.NORMAL, null, null);
    assertDoesNotThrow(() -> MeterReading.validateCorridor(m, new BigDecimal("120"), Optional.of(prev), Optional.of(next)));
    assertDoesNotThrow(() -> MeterReading.validateCorridor(m, new BigDecimal("150"), Optional.of(prev), Optional.empty()));
    assertThrows(BusinessRuleViolation.class, () -> MeterReading.validateCorridor(m, new BigDecimal("90"), Optional.of(prev), Optional.empty()));
    assertThrows(BusinessRuleViolation.class, () -> MeterReading.validateCorridor(m, new BigDecimal("180"), Optional.of(prev), Optional.of(next)));
  }
  @Test void nonMonotonicAlwaysPasses() {
    AssetMeter m = AssetMeter.register(AssetMeterId.generate(), "t1", UUID.randomUUID(), MeterType.FUEL_CONSUMPTION, MeterUnit.LITER, MeterBehavior.NON_MONOTONIC, "Fuel", null);
    MeterReading prev = MeterReading.record(m, MeterReadingId.generate(), new BigDecimal("150"), MeterUnit.LITER, Instant.now(), Instant.now(), null, MeterReadingType.NORMAL, null, null);
    assertDoesNotThrow(() -> MeterReading.validateCorridor(m, new BigDecimal("10"), Optional.of(prev), Optional.empty()));
  }
  @Test void replacementReference() {
    UUID oldId = UUID.randomUUID();
    AssetMeter m2 = AssetMeter.register(AssetMeterId.generate(), "t1", UUID.randomUUID(), MeterType.ODOMETER, MeterUnit.KM, MeterBehavior.MONOTONIC, "Odo-2", oldId);
    assertEquals(oldId, m2.replacementOf());
  }
}
