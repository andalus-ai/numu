package tech.kayys.numou.history.source.domain;
import org.junit.jupiter.api.Test; import java.time.Instant; import static org.junit.jupiter.api.Assertions.*;
class SourceRelationshipTest {
 @Test void relationshipsAreDirectedAndCannotSelfReference(){var a=HistoricalSourceId.generate();var b=HistoricalSourceId.generate();var r=SourceRelationship.create(a,b,SourceRelationshipType.CITES,EvidenceLocatorType.PAGE,"p. 17",null,Instant.EPOCH);assertEquals(a,r.sourceId());assertEquals(b,r.relatedSourceId());assertThrows(IllegalArgumentException.class,()->SourceRelationship.create(a,a,SourceRelationshipType.CITES,null,null,null,Instant.EPOCH));}
 @Test void locatorTypeAndValueMustTravelTogether(){var a=HistoricalSourceId.generate();var b=HistoricalSourceId.generate();assertThrows(IllegalArgumentException.class,()->SourceRelationship.create(a,b,SourceRelationshipType.CITES,EvidenceLocatorType.PAGE,null,null,Instant.EPOCH));}
}
