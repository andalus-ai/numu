package tech.kayys.numou.history.source.domain;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/** Immutable, methodology-bound assessment of one source dimension. */
public record SourceAssessment(UUID id, HistoricalSourceId sourceId, SourceAssessmentDimension dimension,
 SourceAssessmentConclusion conclusion, String rationale, String methodologyId, String methodologyVersion,
 String assessorRef, Instant assessedAt) {
 public SourceAssessment {
  Objects.requireNonNull(id); Objects.requireNonNull(sourceId); Objects.requireNonNull(dimension);
  Objects.requireNonNull(conclusion); rationale=required(rationale,"rationale",8000);
  methodologyId=required(methodologyId,"methodologyId",200);
  methodologyVersion=required(methodologyVersion,"methodologyVersion",80);
  assessorRef=required(assessorRef,"assessorRef",200); Objects.requireNonNull(assessedAt);
 }
 public static SourceAssessment create(HistoricalSourceId sourceId, SourceAssessmentDimension dimension,
  SourceAssessmentConclusion conclusion, String rationale, String methodologyId, String methodologyVersion,
  String assessorRef, Instant now) {
  return new SourceAssessment(UUID.randomUUID(),sourceId,dimension,conclusion,rationale,methodologyId,methodologyVersion,assessorRef,now);
 }
 private static String required(String v,String f,int max){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");String s=v.trim();if(s.length()>max)throw new IllegalArgumentException(f+" exceeds "+max+" characters");return s;}
}
