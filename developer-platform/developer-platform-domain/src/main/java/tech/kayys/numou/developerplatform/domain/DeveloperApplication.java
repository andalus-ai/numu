package tech.kayys.numou.developerplatform.domain;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
/** Tenant-owned integration registration; credentials and gateway state are separate capabilities. */
public final class DeveloperApplication {
 private static final Pattern SLUG=Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
 private final DeveloperApplicationId id; private final TenantId tenantId; private final String slug;
 private String name,description; private DeveloperApplicationStatus status; private long revision;
 private final Instant createdAt; private Instant updatedAt; private final String createdBy; private String updatedBy;
 private DeveloperApplication(DeveloperApplicationId id,TenantId tenantId,String slug,String name,String description,DeveloperApplicationStatus status,long revision,Instant createdAt,Instant updatedAt,String createdBy,String updatedBy){
  this.id=Objects.requireNonNull(id);this.tenantId=Objects.requireNonNull(tenantId);this.slug=normalizeSlug(slug);this.name=requireName(name);this.description=normalizeDescription(description);this.status=Objects.requireNonNull(status);
  if(revision<1)throw new IllegalArgumentException("revision must be positive");this.revision=revision;this.createdAt=Objects.requireNonNull(createdAt);this.updatedAt=Objects.requireNonNull(updatedAt);if(updatedAt.isBefore(createdAt))throw new IllegalArgumentException("updatedAt precedes createdAt");this.createdBy=requireActor(createdBy);this.updatedBy=requireActor(updatedBy);
 }
 public static DeveloperApplication register(TenantId tenantId,String slug,String name,String description,String actor,Instant now){String who=requireActor(actor);return new DeveloperApplication(DeveloperApplicationId.generate(),tenantId,slug,name,description,DeveloperApplicationStatus.ACTIVE,1,now,now,who,who);}
 public static DeveloperApplication reconstitute(DeveloperApplicationId id,TenantId tenantId,String slug,String name,String description,DeveloperApplicationStatus status,long revision,Instant createdAt,Instant updatedAt,String createdBy,String updatedBy){return new DeveloperApplication(id,tenantId,slug,name,description,status,revision,createdAt,updatedAt,createdBy,updatedBy);}
 public DeveloperApplicationEvent updateProfile(String name,String description,String actor,String correlation,Instant now){
  ensureMutable();String nextName=requireName(name);String nextDescription=normalizeDescription(description);
  return advance("DEVELOPER_APPLICATION_PROFILE_UPDATED",actor,correlation,now,()->{this.name=nextName;this.description=nextDescription;});
 }
 public DeveloperApplicationEvent suspend(String actor,String correlation,Instant now){
  if(status!=DeveloperApplicationStatus.ACTIVE)throw new IllegalStateException("only active applications can be suspended");
  return advance("DEVELOPER_APPLICATION_SUSPENDED",actor,correlation,now,()->status=DeveloperApplicationStatus.SUSPENDED);
 }
 public DeveloperApplicationEvent reactivate(String actor,String correlation,Instant now){
  if(status!=DeveloperApplicationStatus.SUSPENDED)throw new IllegalStateException("only suspended applications can be reactivated");
  return advance("DEVELOPER_APPLICATION_REACTIVATED",actor,correlation,now,()->status=DeveloperApplicationStatus.ACTIVE);
 }
 public DeveloperApplicationEvent archive(String actor,String correlation,Instant now){
  if(status==DeveloperApplicationStatus.ARCHIVED)throw new IllegalStateException("already archived");
  return advance("DEVELOPER_APPLICATION_ARCHIVED",actor,correlation,now,()->status=DeveloperApplicationStatus.ARCHIVED);
 }
 private DeveloperApplicationEvent advance(String type,String actor,String correlation,Instant now,Runnable mutation){
  String who=requireActor(actor);Objects.requireNonNull(now);if(now.isBefore(updatedAt))throw new IllegalArgumentException("updatedAt cannot move backwards");
  long nextRevision=Math.addExact(revision,1);
  DeveloperApplicationEvent event=new DeveloperApplicationEvent(UUID.randomUUID(),tenantId,id,type,nextRevision,who,correlation,now);
  mutation.run();revision=nextRevision;updatedAt=now;updatedBy=who;return event;
 }
 private void ensureMutable(){if(status==DeveloperApplicationStatus.ARCHIVED)throw new IllegalStateException("archived application is immutable");}
 public static String normalizeSlug(String value){if(value==null)throw new IllegalArgumentException("slug is required");String s=value.trim().toLowerCase(Locale.ROOT);if(!SLUG.matcher(s).matches())throw new IllegalArgumentException("slug must be 1-63 lowercase letters, digits or internal hyphens");return s;}
 private static String requireName(String s){if(s==null||s.isBlank()||s.trim().length()>120)throw new IllegalArgumentException("name must contain 1-120 characters");return s.trim();}
 private static String normalizeDescription(String s){String v=s==null?"":s.trim();if(v.length()>2000)throw new IllegalArgumentException("description exceeds 2000 characters");return v;}
 private static String requireActor(String s){if(s==null||s.isBlank()||s.trim().length()>200)throw new IllegalArgumentException("actorId is required");return s.trim();}
 public DeveloperApplicationId id(){return id;}public TenantId tenantId(){return tenantId;}public String slug(){return slug;}public String name(){return name;}public String description(){return description;}public DeveloperApplicationStatus status(){return status;}public long revision(){return revision;}public Instant createdAt(){return createdAt;}public Instant updatedAt(){return updatedAt;}public String createdBy(){return createdBy;}public String updatedBy(){return updatedBy;}
}