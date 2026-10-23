package tech.kayys.numou.workspace.domain;
import java.time.Instant; import java.util.Locale; import java.util.Objects; import java.util.UUID;
/** Organizational container; a space is not, by itself, a security boundary. */
public record Space(UUID id,UUID tenantId,UUID workspaceId,String name,String slug,Instant createdAt) {
 public Space { Objects.requireNonNull(id);Objects.requireNonNull(tenantId);Objects.requireNonNull(workspaceId);name=req(name,"name");slug=req(slug,"slug").toLowerCase(Locale.ROOT);if(!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))throw new IllegalArgumentException("Invalid slug");Objects.requireNonNull(createdAt); }
 public static Space create(UUID tenantId,UUID workspaceId,String name,String slug,Instant now){return new Space(UUID.randomUUID(),tenantId,workspaceId,name,slug,now);}
 private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
