package tech.kayys.numou.reliability.adapter.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * The one ObjectMapper configuration every service must use for money-carrying JSON (events, snapshots, API bodies).
 *
 * <ul>
 *   <li><b>Exact BigDecimals.</b> By default Jackson's tree model strips trailing zeros, so {@code 28000} round-trips as
 *       {@code 2.8E+4}: a different scale, unequal under {@code BigDecimal.equals}, and unreadable in an API response.</li>
 *   <li><b>Plain notation</b> when writing ({@code 28000}, never {@code 2.8E+4}).</li>
 *   <li><b>Unknown properties are ignored</b> so a rolling deployment that adds a field to a stored snapshot does not break
 *       instances still running the previous version. New snapshot fields must therefore be nullable or defaulted.</li>
 * </ul>
 */
public final class JsonMapperConfig {

    private JsonMapperConfig() { }

    @SuppressWarnings("deprecation")
    public static ObjectMapper apply(ObjectMapper mapper) {
        mapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        mapper.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
