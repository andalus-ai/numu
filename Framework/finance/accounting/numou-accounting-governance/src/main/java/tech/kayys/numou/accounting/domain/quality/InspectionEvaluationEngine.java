package tech.kayys.syirkah.accounting.domain.quality;

import java.util.Map;
import java.util.Objects;

public class InspectionEvaluationEngine {

    public record EvaluationResult(
            boolean passed,
            int totalParameters,
            int failedParameters,
            String remarks
    ) {}

    public EvaluationResult evaluate(Specification spec, Map<String, Double> measuredValues) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(measuredValues, "measuredValues must not be null");

        int failed = 0;
        StringBuilder sb = new StringBuilder();

        for (var param : spec.parameters()) {
            Double measured = measuredValues.get(param.name());
            if (measured == null) {
                failed++;
                sb.append("Missing value for: ").append(param.name()).append("; ");
            } else if (!param.isWithinTolerance(measured)) {
                failed++;
                sb.append("Parameter ").append(param.name())
                  .append(" out of spec (measured: ").append(measured)
                  .append(", allowed: [").append(param.minValue()).append(", ").append(param.maxValue()).append("]); ");
            }
        }

        boolean pass = failed == 0;
        return new EvaluationResult(pass, spec.parameters().size(), failed, pass ? "All parameters conform" : sb.toString().trim());
    }
}
