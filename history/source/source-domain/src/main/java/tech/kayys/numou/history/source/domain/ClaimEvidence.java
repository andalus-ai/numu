package tech.kayys.numou.history.source.domain;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One explicit source-to-claim link; repeated or contradictory evidence is preserved, not collapsed. */
public record ClaimEvidence(UUID id, HistoricalClaimId claimId, HistoricalSourceId sourceId,
 EvidenceRelation relation, EvidenceLocatorType locatorType, String locator, String excerpt, Instant recordedAt) {
 public ClaimEvidence {
  Objects.requireNonNull(id,"evidence id"); Objects.requireNonNull(claimId,"claim id");
  Objects.requireNonNull(sourceId,"source id"); Objects.requireNonNull(relation,"relation");
  Objects.requireNonNull(locatorType,"locator type"); locator=required(locator,"locator",1000);
  excerpt=optional(excerpt,8000); Objects.requireNonNull(recordedAt,"recordedAt");
 }
 public static ClaimEvidence create(HistoricalClaimId claimId,HistoricalSourceId sourceId,EvidenceRelation relation,
   EvidenceLocatorType locatorType,String locator,String excerpt,Instant now) {
  return new ClaimEvidence(UUID.randomUUID(),claimId,sourceId,relation,locatorType,locator,excerpt,now);
 }
 private static String required(String v,String f,int max){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");String s=v.trim();if(s.length()>max)throw new IllegalArgumentException(f+" too long");return s;}
 private static String optional(String v,int max){if(v==null||v.isBlank())return null;String s=v.trim();if(s.length()>max)throw new IllegalArgumentException("excerpt too long");return s;}
}