package tech.kayys.numou.history.source.domain;
import org.junit.jupiter.api.Test; import java.time.Instant; import static org.junit.jupiter.api.Assertions.*;
class SourceEditionTest {
 @Test void preservesUncertainEditionDateAndValidatesLanguage(){var e=SourceEdition.create(HistoricalSourceId.generate(),"Second critical edition","Archive Press","Jakarta","c. 1920","id-ID","ISBN 978-1","https://example.org/edition/2",null,Instant.EPOCH);assertEquals("c. 1920",e.publicationDateText());assertEquals("id-ID",e.languageTag());assertThrows(IllegalArgumentException.class,()->SourceEdition.create(e.sourceId(),"x",null,null,null,"not_a_tag",null,null,null,Instant.EPOCH));}
 @Test void holdingRequiresInstitutionAndShelfmark(){assertThrows(IllegalArgumentException.class,()->ArchivalHolding.create(HistoricalSourceId.generate(),null,"Archive","Main","Collection",null,ArchivalHoldingType.PHYSICAL,null,null,null,Instant.EPOCH));}
}
