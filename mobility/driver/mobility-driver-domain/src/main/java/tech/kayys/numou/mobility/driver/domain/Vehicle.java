package tech.kayys.numou.mobility.driver.domain;

import tech.kayys.numou.mobility.kernel.domain.VehicleType;

import java.util.Objects;

/** The registered vehicle. The plate is shown to riders so they can recognise the car. */
public record Vehicle(VehicleType type, String plate, String model, String color) {

    public Vehicle {
        Objects.requireNonNull(type, "type cannot be null");
        if (plate == null || plate.isBlank() || plate.length() > 15) {
            throw new IllegalArgumentException("Plate must be 1-15 characters");
        }
        plate = plate.trim().toUpperCase(java.util.Locale.ROOT);
        model = model == null ? "" : model;
        color = color == null ? "" : color;
    }
}
