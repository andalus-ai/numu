package tech.kayys.numou.content.spi;
import java.util.Map;
/** Adapter-owned serialization of validated structured values into the persisted content document. */
public interface ContentPayloadCodec { String encode(Map<String, ?> values); }
