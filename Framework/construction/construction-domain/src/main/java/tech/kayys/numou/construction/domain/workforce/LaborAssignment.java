package tech.kayys.syirkah.construction.domain.workforce;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record LaborAssignment(UUID workerRefId, String trade, LocalDate assignedDate) {
    public LaborAssignment {
        Objects.requireNonNull(workerRefId);
        Objects.requireNonNull(trade);
        Objects.requireNonNull(assignedDate);
    }
}
