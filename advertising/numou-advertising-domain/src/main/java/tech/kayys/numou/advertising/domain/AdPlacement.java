package tech.kayys.numou.advertising.domain;
import java.time.Instant;
import java.util.Objects;
/** Publisher-defined inventory slot. It is not yet eligible for serving until its property is verified and the placement is enabled. */
public record AdPlacement(AdPlacementId id,TenantId tenantId,PublisherPropertyId propertyId,
                          String code,String format,PlacementStatus status,Instant createdAt,long version) {
  public AdPlacement {
    Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(propertyId);
    Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
    code=requiredToken(code,"placement code"); format=requiredToken(format,"format");
    if(version<0) throw new IllegalArgumentException("version cannot be negative");
  }
  public static AdPlacement define(AdPlacementId id,TenantId tenant,PublisherPropertyId property,String code,String format,Instant now){
    return new AdPlacement(id,tenant,property,code,format,PlacementStatus.DRAFT,now,0);
  }
  public AdPlacement enable(){ if(status==PlacementStatus.RETIRED) throw new IllegalStateException("retired placement cannot be enabled"); return new AdPlacement(id,tenantId,propertyId,code,format,PlacementStatus.ENABLED,createdAt,version+1); }
  public AdPlacement disable(){ if(status==PlacementStatus.RETIRED) return this; return new AdPlacement(id,tenantId,propertyId,code,format,PlacementStatus.DISABLED,createdAt,version+1); }
  private static String requiredToken(String value,String label){ Objects.requireNonNull(value,label); String v=value.trim(); if(v.isEmpty()||v.length()>100||!v.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) throw new IllegalArgumentException("invalid "+label); return v; }
}
