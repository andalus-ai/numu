package tech.kayys.numou.history.source.domain;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class SourceAssessmentTest {
 @Test void requiresMethodologyAndActorAndKeepsQualitativeConclusion() {
  var source=HistoricalSourceId.generate();
  var assessment=SourceAssessment.create(source,SourceAssessmentDimension.RELIABILITY,
   SourceAssessmentConclusion.UNCERTAIN,"Only one independent witness is available.","numou.history.source-criticism","1.0","user:historian-1",Instant.parse("2026-11-09T00:00:00Z"));
  assertEquals(SourceAssessmentConclusion.UNCERTAIN,assessment.conclusion());
  assertEquals("numou.history.source-criticism",assessment.methodologyId());
  assertThrows(IllegalArgumentException.class,()->SourceAssessment.create(source,SourceAssessmentDimension.AUTHENTICITY,
   SourceAssessmentConclusion.VERIFIED,"","method","1","actor",Instant.now()));
 }
 @Test void reviewIsAnIndependentImmutableDecision() {
  var assessment=SourceAssessment.create(HistoricalSourceId.generate(),SourceAssessmentDimension.PROVENANCE,
   SourceAssessmentConclusion.PLAUSIBLE,"Chain partially documented.","method","1","actor:a",Instant.now());
  var review=SourceAssessmentReview.create(assessment.id(),SourceAssessmentReviewDecision.REQUEST_CHANGES,
   "Add repository accession evidence.","editor:b",Instant.now());
  assertEquals(assessment.id(),review.assessmentId());
  assertEquals(SourceAssessmentReviewDecision.REQUEST_CHANGES,review.decision());
 }
}
