package tech.kayys.numou.history.source.domain;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class HistoricalSourceTest {
 private static final Instant NOW=Instant.parse("2026-11-07T00:00:00Z");
 @Test void registrationPreservesOriginalPublicationDateExpressionAndNormalizesLanguage() {
  var source=HistoricalSource.register(HistoricalSourceId.generate(),HistoricalSourceKind.MANUSCRIPT,
   "Chronicle","Copy A",null,"https://archive.example/item/1","Archive","c. 1200 CE","id-id",NOW);
  assertEquals("c. 1200 CE",source.publicationDateText()); assertEquals("id-ID",source.languageTag());
  assertEquals(1,source.revision());
 }
 @Test void revisionsAreMonotonicAndArchivedSourcesAreImmutable() {
  var source=HistoricalSource.register(HistoricalSourceId.generate(),HistoricalSourceKind.BOOK,"Title",null,null,null,null,null,null,NOW);
  var revised=source.revise(HistoricalSourceKind.BOOK,"New title",null,null,null,null,null,null,NOW.plusSeconds(1));
  assertEquals(2,revised.revision());
  var archived=revised.archive(NOW.plusSeconds(2));
  assertThrows(IllegalStateException.class,()->archived.revise(HistoricalSourceKind.BOOK,"Again",null,null,null,null,null,null,NOW.plusSeconds(3)));
 }
 @Test void rejectsUnsupportedSchemesAndInvalidLanguageTags() {
  assertThrows(IllegalArgumentException.class,()->HistoricalSource.register(HistoricalSourceId.generate(),HistoricalSourceKind.WEBSITE,"Site",null,null,"javascript:alert(1)",null,null,null,NOW));
  assertThrows(IllegalArgumentException.class,()->HistoricalSource.register(HistoricalSourceId.generate(),HistoricalSourceKind.BOOK,"Book",null,null,null,null,null,"not a tag",NOW));
 }
 @Test void contradictoryEvidenceRemainsASeparateRelation() {
  var evidence=ClaimEvidence.create(HistoricalClaimId.generate(),HistoricalSourceId.generate(),EvidenceRelation.CONTRADICTS,EvidenceLocatorType.PAGE,"p. 12","The source disputes the claim",NOW);
  assertEquals(EvidenceRelation.CONTRADICTS,evidence.relation());
 }
}