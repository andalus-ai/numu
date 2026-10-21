package tech.kayys.numou.mobility.driver.application;

import tech.kayys.numou.mobility.driver.domain.Driver;

import java.util.UUID;

/** What a rider sees of the driver who accepted: name, vehicle and rating - never the phone number. */
public record DriverView(UUID id, String name, String vehicleType, String plate, String model, String color, double rating, double acceptanceRate, String status, String verification) {

    public static DriverView of(Driver d) {
        return new DriverView(d.id().value(), d.name(), d.vehicle().type().name(), d.vehicle().plate(), d.vehicle().model(),
                d.vehicle().color(), Math.round(d.rating() * 10) / 10.0, d.acceptanceRate(), d.status().name(), d.verification().name());
    }
}
