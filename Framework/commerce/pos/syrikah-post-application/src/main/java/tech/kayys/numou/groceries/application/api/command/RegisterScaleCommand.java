package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.groceries.domain.identifier.ScaleId;
import tech.kayys.syirkah.groceries.domain.model.ScaleDevice;

/**
 * Command to register a scale device.
 */
public record RegisterScaleCommand(
        ScaleId scaleId,
        String deviceName,
        String model,
        String serialNumber,
        String manufacturer,
        ScaleDevice.ScaleType scaleType,
        Double maxWeightKg,
        Double minWeightKg,
        Double accuracyGrams
) implements Command {

    public RegisterScaleCommand {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Device name is required");
        }
        if (scaleType == null) {
            throw new IllegalArgumentException("Scale type is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ScaleId scaleId;
        private String deviceName;
        private String model;
        private String serialNumber;
        private String manufacturer;
        private ScaleDevice.ScaleType scaleType;
        private Double maxWeightKg;
        private Double minWeightKg;
        private Double accuracyGrams;

        public Builder scaleId(ScaleId scaleId) {
            this.scaleId = scaleId;
            return this;
        }

        public Builder deviceName(String deviceName) {
            this.deviceName = deviceName;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder serialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        public Builder manufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
            return this;
        }

        public Builder scaleType(ScaleDevice.ScaleType scaleType) {
            this.scaleType = scaleType;
            return this;
        }

        public Builder maxWeightKg(Double maxWeightKg) {
            this.maxWeightKg = maxWeightKg;
            return this;
        }

        public Builder minWeightKg(Double minWeightKg) {
            this.minWeightKg = minWeightKg;
            return this;
        }

        public Builder accuracyGrams(Double accuracyGrams) {
            this.accuracyGrams = accuracyGrams;
            return this;
        }

        public RegisterScaleCommand build() {
            if (scaleId == null) {
                scaleId = ScaleId.generate();
            }
            return new RegisterScaleCommand(
                scaleId, deviceName, model, serialNumber, manufacturer,
                scaleType, maxWeightKg, minWeightKg, accuracyGrams
            );
        }
    }
}
