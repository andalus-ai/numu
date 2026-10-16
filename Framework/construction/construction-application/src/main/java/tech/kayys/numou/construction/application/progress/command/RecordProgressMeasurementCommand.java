package tech.kayys.syirkah.construction.application.progress.command;

import tech.kayys.syirkah.construction.domain.progress.MeasurementQuantity;
import tech.kayys.syirkah.construction.domain.progress.MeasurementType;
import tech.kayys.syirkah.foundation.application.command.Command;
import java.time.LocalDate;
import java.util.UUID;

public record RecordProgressMeasurementCommand(
        UUID projectId,
        UUID boqId,
        UUID boqItemId,
        LocalDate measurementDate,
        MeasurementType type,
        MeasurementQuantity quantity,
        String remarks
) implements Command {}
