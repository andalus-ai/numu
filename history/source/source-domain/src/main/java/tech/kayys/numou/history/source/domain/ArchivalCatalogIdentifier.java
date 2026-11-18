package tech.kayys.numou.history.source.domain;
import java.time.Instant; import java.util.Objects; import java.util.UUID;
/** Identifiers are scoped to one holding and one assigning authority. */
public record ArchivalCatalogIdentifier(UUID id,UUID holdingId,String scheme,String value,String assigningAuthority,Instant recordedAt) {
 public ArchivalCatalogIdentifier {Objects.requireNonNull(id);Objects.requireNonNull(holdingId);scheme=required(scheme,"scheme",80);value=required(value,"value",500);assigningAuthority=optional(assigningAuthority,500);Objects.requireNonNull(recordedAt);}
 public static ArchivalCatalogIdentifier create(UUID holdingId,String scheme,String value,String authority,Instant now){return new ArchivalCatalogIdentifier(UUID.randomUUID(),holdingId,scheme,value,authority,now);}
 private static String required(String v,String f,int n){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");String s=v.trim();if(s.length()>n)throw new IllegalArgumentException(f+" exceeds "+n+" characters");return s;}
 private static String optional(String v,int n){if(v==null||v.isBlank())return null;String s=v.trim();if(s.length()>n)throw new IllegalArgumentException("value exceeds "+n+" characters");return s;}
}
