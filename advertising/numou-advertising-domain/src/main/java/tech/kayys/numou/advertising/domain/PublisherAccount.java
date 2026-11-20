package tech.kayys.numou.advertising.domain;
import java.time.Instant;
import java.util.Objects;
/** Publisher participation record; property ownership verification is a separate workflow. */
public record PublisherAccount(AdvertisingAccountId id, TenantId tenantId, PartyReference party,
                               String displayName, AccountStatus status, Instant createdAt, long version) {
  public PublisherAccount {
    Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(party);
    Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
    displayName=AdvertiserAccount.required(displayName,"display name");
    if(version<0) throw new IllegalArgumentException("version cannot be negative");
  }
  public static PublisherAccount register(AdvertisingAccountId id,TenantId tenant,PartyReference party,String name,Instant now){
    return new PublisherAccount(id,tenant,party,name,AccountStatus.PENDING,now,0);
  }
  public PublisherAccount activate(){ requireNotClosed(); return new PublisherAccount(id,tenantId,party,displayName,AccountStatus.ACTIVE,createdAt,version+1); }
  public PublisherAccount suspend(){ requireNotClosed(); return new PublisherAccount(id,tenantId,party,displayName,AccountStatus.SUSPENDED,createdAt,version+1); }
  public PublisherAccount close(){ if(status==AccountStatus.CLOSED)return this; return new PublisherAccount(id,tenantId,party,displayName,AccountStatus.CLOSED,createdAt,version+1); }
  private void requireNotClosed(){ if(status==AccountStatus.CLOSED) throw new IllegalStateException("closed publisher account cannot transition"); }
}
