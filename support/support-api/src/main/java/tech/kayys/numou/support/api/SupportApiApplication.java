package tech.kayys.numou.support.api;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class SupportApiApplication implements QuarkusApplication {
    @Override
    public int run(String... args) {
        // This class is mainly for providing a main method if needed.
        // In Quarkus, the application starts automatically.
        return 0;
    }
}