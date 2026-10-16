package tech.kayys.syirkah.construction.application.boq.command;

import tech.kayys.syirkah.construction.domain.boq.BoqCalculationPolicy;
import tech.kayys.syirkah.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

/** Actor identity is intentionally absent: handlers obtain it from trusted security context. */
public record CaptureBoqRevisionCommand(UUID boqId, BoqCalculationPolicy calculationPolicy) implements Command {
    public CaptureBoqRevisionCommand {
        Objects.requireNonNull(boqId, "BOQ id cannot be null");
        Objects.requireNonNull(calculationPolicy, "Calculation policy cannot be null");
    }
}
