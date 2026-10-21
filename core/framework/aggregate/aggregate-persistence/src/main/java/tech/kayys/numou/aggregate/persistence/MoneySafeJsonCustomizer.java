package tech.kayys.numou.aggregate.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import tech.kayys.numou.reliability.adapter.json.JsonMapperConfig;

/** Applies {@link JsonMapperConfig} to Quarkus' shared ObjectMapper (API bodies, snapshots, outbox payloads). NOT COMPILED in sandbox. */
@Singleton
public class MoneySafeJsonCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        JsonMapperConfig.apply(mapper);
    }
}
