package tech.kayys.numou.plugin.application.resolution;

import java.util.List;

public final class CapabilityResolutionException extends IllegalStateException {
    private final List<String> diagnostics;

    public CapabilityResolutionException(String message) {
        this(List.of(message));
    }

    public CapabilityResolutionException(List<String> diagnostics) {
        super(String.join("; ", List.copyOf(diagnostics)));
        if (diagnostics == null || diagnostics.isEmpty()) {
            throw new IllegalArgumentException("At least one capability resolution diagnostic is required");
        }
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<String> diagnostics() {
        return diagnostics;
    }
}
