package io.surveyhub.presentation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class Resources {
    private Resources() {}

    static String text(String name) {
        try (InputStream in = Resources.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("Missing resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
