package tech.kayys.numou.asset.infrastructure.persistence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.meter.MeterBehavior;
import tech.kayys.numou.asset.domain.meter.MeterType;
import tech.kayys.numou.asset.domain.meter.MeterUnit;
import tech.kayys.numou.foundation.persistence.BaseEntity;
import java.util.UUID;
@Entity
@Table(name = "asset_meter")
public class AssetMeterEntity extends BaseEntity {
  @Column(name = "tenant_id", nullable = false, length = 100)
  public String tenantId;
  @Column(name = "asset_id", nullable = false)
  public UUID assetId;
  @Enumerated(EnumType.STRING)
  @Column(name = "meter_type", nullable = false, length = 50)
  public MeterType meterType;
  @Enumerated(EnumType.STRING)
  @Column(name = "unit", nullable = false, length = 50)
  public MeterUnit unit;
  @Enumerated(EnumType.STRING)
  @Column(name = "behavior", nullable = false, length = 50)
  public MeterBehavior behavior;
  @Column(name = "name", nullable = false, length = 255)
  public String name;
  @Column(name = "replacement_of")
  public UUID replacementOf;
}
