package tech.kayys.numou.mobility.ride.application;

import java.time.Instant;

/**
 * What anyone with a share link may see: the trip's status, the driver's <em>first name</em>, the vehicle (so the contact can verify it
 * is the right car), and the live position while the trip is active. No rider name, no addresses, no PIN, no phone, no fare.
 */
public record PublicTrackingView(String status, boolean ended, String driverFirstName, String vehicleType, String plate, String model,
                                 String color, Double lat, Double lng, Instant updatedAt) { }
