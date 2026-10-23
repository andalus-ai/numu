package tech.kayys.numou.workspace.persistence.jdbc;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JdbcWorkspaceStoreContractTest {
 @Test void blockAttributesRoundTripDelimitersUnicodeAndEmptyValues() {
  Map<String,String> attributes=Map.of("referenceType","finance.invoice","referenceId","a:b;c","unicode","café 東京","empty","");
  assertEquals(attributes,BlockAttributesCodec.decode(BlockAttributesCodec.encode(attributes)));
 }
 @Test void emptyAttributesRoundTrip() {
  assertEquals(Map.of(),BlockAttributesCodec.decode(BlockAttributesCodec.encode(Map.of())));
 }
 @Test void malformedAttributeEncodingIsRejected() {
  assertThrows(IllegalStateException.class,()->BlockAttributesCodec.decode("missing-separator"));
 }
}
