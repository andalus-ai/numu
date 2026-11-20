package tech.kayys.numou.history.source.domain;
import java.util.Objects; import java.util.UUID;
/** A source-specific attribution. authorityRef may point to a canonical Person without owning that identity. */
public record SourceContributor(UUID id, HistoricalSourceId sourceId, String displayName, String authorityRef,
 SourceContributorRole role, int sequence, String attributionNote) {
 public SourceContributor { Objects.requireNonNull(id); Objects.requireNonNull(sourceId); displayName=required(displayName,"displayName",500); Objects.requireNonNull(role); if(sequence<0)throw new IllegalArgumentException("sequence cannot be negative"); attributionNote=optional(attributionNote,4000); authorityRef=optional(authorityRef,500); }
 public static SourceContributor create(HistoricalSourceId sourceId,String displayName,String authorityRef,SourceContributorRole role,int sequence,String note){return new SourceContributor(UUID.randomUUID(),sourceId,displayName,authorityRef,role,sequence,note);}
 private static String required(String v,String f,int n){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");String s=v.trim();if(s.length()>n)throw new IllegalArgumentException(f+" exceeds "+n+" characters");return s;}
 private static String optional(String v,int n){if(v==null||v.isBlank())return null;String s=v.trim();if(s.length()>n)throw new IllegalArgumentException("value exceeds "+n+" characters");return s;}
}
