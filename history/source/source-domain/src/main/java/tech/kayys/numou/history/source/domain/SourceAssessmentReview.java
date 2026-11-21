package tech.kayys.numou.history.source.domain;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/** Immutable editorial decision; reviews never rewrite the original assessment. */
public record SourceAssessmentReview(UUID id, UUID assessmentId, SourceAssessmentReviewDecision decision,
 String rationale, String reviewerRef, Instant reviewedAt) {
 public SourceAssessmentReview {
  Objects.requireNonNull(id); Objects.requireNonNull(assessmentId); Objects.requireNonNull(decision);
  rationale=required(rationale); reviewerRef=required(reviewerRef); Objects.requireNonNull(reviewedAt);
 }
 public static SourceAssessmentReview create(UUID assessmentId,SourceAssessmentReviewDecision decision,
  String rationale,String reviewerRef,Instant now){return new SourceAssessmentReview(UUID.randomUUID(),assessmentId,decision,rationale,reviewerRef,now);}
 private static String required(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("review rationale and reviewerRef are required");String s=v.trim();if(s.length()>8000)throw new IllegalArgumentException("review rationale exceeds 8000 characters");return s;}
}
