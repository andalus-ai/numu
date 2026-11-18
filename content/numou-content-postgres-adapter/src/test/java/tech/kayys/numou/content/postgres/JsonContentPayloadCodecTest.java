package tech.kayys.numou.content.postgres;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonContentPayloadCodecTest {
    private final JsonContentPayloadCodec codec=new JsonContentPayloadCodec();

    @Test void encodesDeterministicNestedJsonAndEscapesControlCharacters() {
        Map<String,Object> values=new HashMap<>();
        values.put("z",List.of(1,true,nullSafe("x\n\"")));
        values.put("a",new BigDecimal("12.500"));
        assertEquals("{\"a\":12.5,\"z\":[1,true,\"x\\n\\\"\"]}",codec.encode(values));
    }

    @Test void rejectsCyclesUnsupportedValuesAndNonFiniteNumbers() {
        Map<String,Object> cycle=new HashMap<>();cycle.put("self",cycle);
        assertThrows(IllegalArgumentException.class,()->codec.encode(cycle));
        assertThrows(IllegalArgumentException.class,()->codec.encode(Map.of("bad",new Object())));
        assertThrows(IllegalArgumentException.class,()->codec.encode(Map.of("bad",Double.NaN)));
    }

    private static String nullSafe(String value){return value;}
}
