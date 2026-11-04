package tech.kayys.numou.developer.apicatalog.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.*;

/** Tenant-owned API product metadata; gateway routes and credentials are separate capabilities. */
public final class ApiProduct extends AbstractAggregateRoot<ApiProductId> {
 private final ApiProductId id;
 private final UUID providerTenantId;
 private final Instant createdAt;
 private final Map<String,ApiVersion> versions=new TreeMap<>();
 private final String name;
 private final String slug;
 private final String description;
 private ApiProductStatus status;
 private Instant updatedAt;
 private long revision;

 private ApiProduct(ApiProductId id,UUID tenant,String name,String slug,String description,Instant now){
  super(Objects.requireNonNull(id,"id cannot be null"));
  this.id=id;this.providerTenantId=Objects.requireNonNull(tenant,"providerTenantId cannot be null");
  this.name=text(name,"name");if(this.name.length()>200)throw new IllegalArgumentException("name exceeds 200 characters");this.slug=slug(slug);this.description=description(description);
  this.status=ApiProductStatus.DRAFT;this.createdAt=Objects.requireNonNull(now,"now cannot be null");
  this.updatedAt=now;this.revision=1;
  raise(new ApiProductCreated(UUID.randomUUID(),now,id,tenant,this.slug));
 }
 private ApiProduct(ApiProductId id,UUID tenant,String name,String slug,String description,ApiProductStatus status,
                    Instant createdAt,Instant updatedAt,long revision,Collection<ApiVersion> versions){
  super(Objects.requireNonNull(id,"id cannot be null"));
  this.id=id;this.providerTenantId=Objects.requireNonNull(tenant,"providerTenantId cannot be null");
  this.name=text(name,"name");this.slug=slug(slug);this.description=description(description);
  this.status=Objects.requireNonNull(status,"status cannot be null");
  this.createdAt=Objects.requireNonNull(createdAt,"createdAt cannot be null");
  this.updatedAt=Objects.requireNonNull(updatedAt,"updatedAt cannot be null");
  if(updatedAt.isBefore(createdAt))throw new IllegalArgumentException("updatedAt cannot be before createdAt");
  if(revision<1)throw new IllegalArgumentException("revision must be positive");
  this.revision=revision;
  for(ApiVersion version:Objects.requireNonNull(versions,"versions cannot be null")){
   Objects.requireNonNull(version,"version cannot be null");
   if(this.versions.putIfAbsent(version.version(),version)!=null)throw new IllegalArgumentException("duplicate API version: "+version.version());
  }
 }
 public static ApiProduct create(ApiProductId id,UUID tenant,String name,String slug,String description,Instant now){
  return new ApiProduct(id,tenant,name,slug,description,now);
 }
 /** Reconstitutes persisted state without emitting creation events or changing the stored revision. */
 public static ApiProduct reconstitute(ApiProductId id,UUID tenant,String name,String slug,String description,
                                       ApiProductStatus status,Instant createdAt,Instant updatedAt,long revision,
                                       Collection<ApiVersion> versions){
  return new ApiProduct(id,tenant,name,slug,description,status,createdAt,updatedAt,revision,versions);
 }
 public void addVersion(ApiVersion version,Instant now){mutable(now);Objects.requireNonNull(version,"version cannot be null");if(status==ApiProductStatus.RETIRED)throw new IllegalStateException("retired product cannot be changed");if(version.status()!=ApiVersionStatus.DRAFT)throw new IllegalArgumentException("only draft versions can be added");if(versions.putIfAbsent(version.version(),version)!=null)throw new IllegalStateException("API version already exists: "+version.version());touch(now);}
 public void publishVersion(String version,Instant now){mutable(now);ApiVersion v=requireVersion(version);if(status==ApiProductStatus.SUSPENDED||status==ApiProductStatus.RETIRED)throw new IllegalStateException("suspended or retired product cannot publish versions");versions.put(version,v.publish());if(status==ApiProductStatus.DRAFT)status=ApiProductStatus.ACTIVE;touch(now);raise(new ApiVersionPublished(UUID.randomUUID(),now,id,version));}
 public void deprecateVersion(String version,Instant now){mutable(now);versions.put(version,requireVersion(version).deprecate());touch(now);}
 public void retireVersion(String version,Instant now){mutable(now);versions.put(version,requireVersion(version).retire());touch(now);}
 public void suspend(Instant now){mutable(now);if(status==ApiProductStatus.RETIRED)throw new IllegalStateException("retired product cannot be suspended");if(status==ApiProductStatus.SUSPENDED)return;status=ApiProductStatus.SUSPENDED;touch(now);}
 public void reactivate(Instant now){mutable(now);if(status!=ApiProductStatus.SUSPENDED)throw new IllegalStateException("only suspended products can be reactivated");status=versions.values().stream().anyMatch(v->v.status()==ApiVersionStatus.PUBLISHED)?ApiProductStatus.ACTIVE:ApiProductStatus.DRAFT;touch(now);}
 public void retire(Instant now){mutable(now);if(status==ApiProductStatus.RETIRED)return;if(status!=ApiProductStatus.SUSPENDED)throw new IllegalStateException("product must be suspended before retirement");status=ApiProductStatus.RETIRED;touch(now);}
 private ApiVersion requireVersion(String v){ApiVersion x=versions.get(text(v,"version"));if(x==null)throw new IllegalArgumentException("unknown API version: "+v);return x;}
 private void mutable(Instant now){Objects.requireNonNull(now,"now cannot be null");if(now.isBefore(updatedAt))throw new IllegalArgumentException("now cannot be before updatedAt");}
 private void touch(Instant now){updatedAt=now;revision=Math.addExact(revision,1);}
 private static String slug(String v){String n=text(v,"slug").toLowerCase(Locale.ROOT);if(n.length()>120||!n.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))throw new IllegalArgumentException("invalid API product slug");return n;}
 private static String description(String v){if(v==null)return "";String n=v.trim();if(n.length()>2000)throw new IllegalArgumentException("description exceeds 2000 characters");return n;}
 private static String text(String v,String f){Objects.requireNonNull(v,f+" cannot be null");String n=v.trim();if(n.isEmpty())throw new IllegalArgumentException(f+" cannot be blank");return n;}
 public ApiProductId id(){return id;} public UUID providerTenantId(){return providerTenantId;} public String name(){return name;} public String slug(){return slug;} public String description(){return description;} public ApiProductStatus status(){return status;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;} public long revision(){return revision;} public Map<String,ApiVersion> versions(){return Map.copyOf(versions);}
}
