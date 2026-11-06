package tech.kayys.numou.advertising.domain;
import java.time.Instant;
import java.util.Objects;
/** Advertising participation record; the commercial Party remains externally owned. */
public record AdvertiserAccount(AdvertisingAccountId id, TenantId tenantId, PartyReference party,
                                String displayName, AccountStatus status, Instant createdAt, long version) {
  public AdvertiserAccount {
    Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(party);
    Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
    displayName=required(displayName,"display name");
    if(version<0) throw new IllegalArgumentException("version cannot be negative");
  }
  public static AdvertiserAccount register(AdvertisingAccountId id,TenantId tenant,PartyReference party,String name,Instant now){
    return new AdvertiserAccount(id,tenant,party,name,AccountStatus.PENDING,now,0);
  }
  public AdvertiserAccount activate(){ requireNotClosed(); return new AdvertiserAccount(id,tenantId,party,displayName,AccountStatus.ACTIVE,createdAt,version+1); }
  public AdvertiserAccount suspend(){ requireNotClosed(); return new AdvertiserAccount(id,tenantId,party,displayName,AccountStatus.SUSPENDED,createdAt,version+1); }
  public AdvertiserAccount close(){ if(status==AccountStatus.CLOSED) return this; return new AdvertiserAccount(id,tenantId,party,displayName,AccountStatus.CLOSED,createdAt,version+1); }
  private void requireNotClosed(){ if(status==AccountStatus.CLOSED) throw new IllegalStateException("closed advertiser account cannot transition"); }
  static String required(String value,String label){ Objects.requireNonNull(value,label); String v=value.trim(); if(v.isEmpty()||v.length()>200) throw new IllegalArgumentException(label+" must contain 1..200 characters"); return v; }
}
