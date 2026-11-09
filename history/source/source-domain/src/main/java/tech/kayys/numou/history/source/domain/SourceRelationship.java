package tech.kayys.numou.history.source.domain;
import java.time.Instant; import java.util.Objects; import java.util.UUID;
/** Directed, source-to-source bibliographic relationship; distinct from claim evidence. */
public record SourceRelationship(UUID id,HistoricalSourceId sourceId,HistoricalSourceId relatedSourceId,
 SourceRelationshipType relation,EvidenceLocatorType locatorType,String locator,String note,Instant recordedAt) {
 public SourceRelationship {
  Objects.requireNonNull(id);Objects.requireNonNull(sourceId);Objects.requireNonNull(relatedSourceId);
  if(sourceId.equals(relatedSourceId))throw new IllegalArgumentException("a source cannot relate to itself");
  Objects.requireNonNull(relation);Objects.requireNonNull(recordedAt);
  if((locatorType==null)!=(locator==null||locator.isBlank()))throw new IllegalArgumentException("locator type and locator must be supplied together");
  if(locator!=null){locator=locator.trim();if(locator.length()>1000)throw new IllegalArgumentException("locator exceeds 1000 characters");}
  note=optional(note,4000);
 }
 public static SourceRelationship create(HistoricalSourceId sourceId,HistoricalSourceId relatedSourceId,SourceRelationshipType relation,EvidenceLocatorType locatorType,String locator,String note,Instant now){
  return new SourceRelationship(UUID.randomUUID(),sourceId,relatedSourceId,relation,locatorType,locator,note,now);
 }
 private static String optional(String value,int max){if(value==null||value.isBlank())return null;String s=value.trim();if(s.length()>max)throw new IllegalArgumentException("note exceeds "+max+" characters");return s;}
}
