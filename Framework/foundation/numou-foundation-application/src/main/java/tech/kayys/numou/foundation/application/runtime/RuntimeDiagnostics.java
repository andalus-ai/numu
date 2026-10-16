package tech.kayys.numou.foundation.application.runtime;

import java.time.Instant;
import java.util.Map;

/**
 * Diagnostic metrics and operational metadata exposed by the Application Runtime.
 */
public interface RuntimeDiagnostics {

    Instant startedAt();

    long uptimeMillis();

    Map<String, Object> diagnosticData();
}
