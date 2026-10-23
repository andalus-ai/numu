package tech.kayys.numou.workspace.domain;
import java.time.Instant; import java.util.Objects; import java.util.UUID;
/** Tenant-owned collaboration container; it does not own domain-module business records. */
public record Workspace(UUID id, UUID tenantId, String name, String description, Instant createdAt) {
 public Workspace { Objects.requireNonNull(id); Objects.requireNonNull(tenantId); name=req(name,"name"); description=description==null?"":description.trim(); Objects.requireNonNull(createdAt); }
 public static Workspace create(UUID tenantId,String name,String description,Instant now){return new Workspace(UUID.randomUUID(),tenantId,name,description,now);}
 private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
