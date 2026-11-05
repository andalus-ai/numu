package tech.kayys.numou.advertising.domain;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
/** A publisher-controlled website, application or channel. */
public record PublisherProperty(PublisherPropertyId id,TenantId tenantId,AdvertisingAccountId publisherId,
                                String name,URI canonicalUri,PropertyStatus status,Instant createdAt,long version) {
  public PublisherProperty {
    Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(publisherId);
    Objects.requireNonNull(canonicalUri); Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
    name=AdvertiserAccount.required(name,"property name");
    String scheme=canonicalUri.getScheme();
    if(!canonicalUri.isAbsolute() || canonicalUri.getHost()==null || scheme==null || !(scheme.equalsIgnoreCase("https")||scheme.equalsIgnoreCase("http")) )
      throw new IllegalArgumentException("property URI must be an absolute HTTP(S) URI with a host");
    if(canonicalUri.toString().length()>2048) throw new IllegalArgumentException("property URI exceeds 2048 characters");
    if(version<0) throw new IllegalArgumentException("version cannot be negative");
  }
  public static PublisherProperty register(PublisherPropertyId id,TenantId tenant,AdvertisingAccountId publisher,String name,URI uri,Instant now){
    return new PublisherProperty(id,tenant,publisher,name,uri,PropertyStatus.PENDING_VERIFICATION,now,0);
  }
  public PublisherProperty verify(){ if(status==PropertyStatus.SUSPENDED||status==PropertyStatus.RETIRED) throw new IllegalStateException("property cannot be verified in current state"); return new PublisherProperty(id,tenantId,publisherId,name,canonicalUri,PropertyStatus.VERIFIED,createdAt,version+1); }
  public PublisherProperty suspend(){ if(status==PropertyStatus.RETIRED) throw new IllegalStateException("retired property cannot be suspended"); return new PublisherProperty(id,tenantId,publisherId,name,canonicalUri,PropertyStatus.SUSPENDED,createdAt,version+1); }
}
