package tech.kayys.numou.asset.infrastructure.persistence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.meter.MeterReadingType;
import tech.kayys.numou.asset.domain.meter.MeterUnit;
import tech.kayys.numou.foundation.persistence.BaseEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "asset_meter_reading")
public class MeterReadingEntity extends BaseEntity {
  @Column(name = "tenant_id", nullable = false, length = 100)
  public String tenantId;
  @Column(name = "meter_id", nullable = false)
  public UUID meterId;
  @Column(name = "asset_id", nullable = false)
  public UUID assetId;
  @Column(name = "reading_value", nullable = false, precision = 24, scale = 8)
  public BigDecimal value;
  @Enumerated(EnumType.STRING)
  @Column(name = "unit", nullable = false, length = 50)
  public MeterUnit unit;
  @Column(name = "recorded_at", nullable = false)
  public Instant recordedAt;
  @Column(name = "occurred_at", nullable = false)
  public Instant occurredAt;
  @Column(name = "recorded_by", length = 100)
  public String recordedBy;
  @Enumerated(EnumType.STRING)
  @Column(name = "reading_type", nullable = false, length = 50)
  public MeterReadingType readingType;
  @Column(name = "source", length = 100)
  public String source;
  @Column(name = "source_ref", length = 200)
  public String sourceRef;
}
