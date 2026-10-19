package tech.kayys.numou.fms.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Fleet analytics value object.
 * Provides key performance indicators for fleet operations.
 */
public final class FleetAnalytics implements ValueObject {
    
    private static final long serialVersionUID = 1L;
    
    // Fleet Metrics
    private final int totalVehicles;
    private final int activeVehicles;
    private final int vehiclesInMaintenance;
    private final int availableVehicles;
    private final double utilizationRate;
    
    // Driver Metrics
    private final int totalDrivers;
    private final int activeDrivers;
    private final int driversOnTrip;
    private final double driverAvailabilityRate;
    
    // Operational Metrics
    private final double totalDistanceTraveled;
    private final double totalFuelConsumed;
    private final double averageFuelEfficiency;
    private final double totalRevenue;
    private final double costPerKilometer;
    private final double revenuePerKilometer;
    
    // Safety Metrics
    private final double averageSafetyScore;
    private final int totalAccidents;
    private final int totalCitations;
    private final double incidentsPerMillionKm;
    
    // Maintenance Metrics
    private final double averageMaintenanceCost;
    private final double maintenanceCostPerKm;
    private final int vehiclesDueForMaintenance;
    
    // Compliance Metrics
    private final int expiredLicenses;
    private final int expiredInsurance;
    private final int expiredDocuments;
    
    // Timestamp
    private final Instant calculatedAt;

    private FleetAnalytics(Builder builder) {
        this.totalVehicles = builder.totalVehicles;
        this.activeVehicles = builder.activeVehicles;
        this.vehiclesInMaintenance = builder.vehiclesInMaintenance;
        this.availableVehicles = builder.availableVehicles;
        this.utilizationRate = builder.utilizationRate;
        this.totalDrivers = builder.totalDrivers;
        this.activeDrivers = builder.activeDrivers;
        this.driversOnTrip = builder.driversOnTrip;
        this.driverAvailabilityRate = builder.driverAvailabilityRate;
        this.totalDistanceTraveled = builder.totalDistanceTraveled;
        this.totalFuelConsumed = builder.totalFuelConsumed;
        this.averageFuelEfficiency = builder.averageFuelEfficiency;
        this.totalRevenue = builder.totalRevenue;
        this.costPerKilometer = builder.costPerKilometer;
        this.revenuePerKilometer = builder.revenuePerKilometer;
        this.averageSafetyScore = builder.averageSafetyScore;
        this.totalAccidents = builder.totalAccidents;
        this.totalCitations = builder.totalCitations;
        this.incidentsPerMillionKm = builder.incidentsPerMillionKm;
        this.averageMaintenanceCost = builder.averageMaintenanceCost;
        this.maintenanceCostPerKm = builder.maintenanceCostPerKm;
        this.vehiclesDueForMaintenance = builder.vehiclesDueForMaintenance;
        this.expiredLicenses = builder.expiredLicenses;
        this.expiredInsurance = builder.expiredInsurance;
        this.expiredDocuments = builder.expiredDocuments;
        this.calculatedAt = builder.calculatedAt != null ? builder.calculatedAt : Instant.now();
    }

    // Getters
    public int getTotalVehicles() { return totalVehicles; }
    public int getActiveVehicles() { return activeVehicles; }
    public int getVehiclesInMaintenance() { return vehiclesInMaintenance; }
    public int getAvailableVehicles() { return availableVehicles; }
    public double getUtilizationRate() { return utilizationRate; }
    public int getTotalDrivers() { return totalDrivers; }
    public int getActiveDrivers() { return activeDrivers; }
    public int getDriversOnTrip() { return driversOnTrip; }
    public double getDriverAvailabilityRate() { return driverAvailabilityRate; }
    public double getTotalDistanceTraveled() { return totalDistanceTraveled; }
    public double getTotalFuelConsumed() { return totalFuelConsumed; }
    public double getAverageFuelEfficiency() { return averageFuelEfficiency; }
    public double getTotalRevenue() { return totalRevenue; }
    public double getCostPerKilometer() { return costPerKilometer; }
    public double getRevenuePerKilometer() { return revenuePerKilometer; }
    public double getAverageSafetyScore() { return averageSafetyScore; }
    public int getTotalAccidents() { return totalAccidents; }
    public int getTotalCitations() { return totalCitations; }
    public double getIncidentsPerMillionKm() { return incidentsPerMillionKm; }
    public double getAverageMaintenanceCost() { return averageMaintenanceCost; }
    public double getMaintenanceCostPerKm() { return maintenanceCostPerKm; }
    public int getVehiclesDueForMaintenance() { return vehiclesDueForMaintenance; }
    public int getExpiredLicenses() { return expiredLicenses; }
    public int getExpiredInsurance() { return expiredInsurance; }
    public int getExpiredDocuments() { return expiredDocuments; }
    public Instant getCalculatedAt() { return calculatedAt; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int totalVehicles;
        private int activeVehicles;
        private int vehiclesInMaintenance;
        private int availableVehicles;
        private double utilizationRate;
        private int totalDrivers;
        private int activeDrivers;
        private int driversOnTrip;
        private double driverAvailabilityRate;
        private double totalDistanceTraveled;
        private double totalFuelConsumed;
        private double averageFuelEfficiency;
        private double totalRevenue;
        private double costPerKilometer;
        private double revenuePerKilometer;
        private double averageSafetyScore;
        private int totalAccidents;
        private int totalCitations;
        private double incidentsPerMillionKm;
        private double averageMaintenanceCost;
        private double maintenanceCostPerKm;
        private int vehiclesDueForMaintenance;
        private int expiredLicenses;
        private int expiredInsurance;
        private int expiredDocuments;
        private Instant calculatedAt;

        public Builder totalVehicles(int totalVehicles) { this.totalVehicles = totalVehicles; return this; }
        public Builder activeVehicles(int activeVehicles) { this.activeVehicles = activeVehicles; return this; }
        public Builder vehiclesInMaintenance(int vehiclesInMaintenance) { this.vehiclesInMaintenance = vehiclesInMaintenance; return this; }
        public Builder availableVehicles(int availableVehicles) { this.availableVehicles = availableVehicles; return this; }
        public Builder utilizationRate(double utilizationRate) { this.utilizationRate = utilizationRate; return this; }
        public Builder totalDrivers(int totalDrivers) { this.totalDrivers = totalDrivers; return this; }
        public Builder activeDrivers(int activeDrivers) { this.activeDrivers = activeDrivers; return this; }
        public Builder driversOnTrip(int driversOnTrip) { this.driversOnTrip = driversOnTrip; return this; }
        public Builder driverAvailabilityRate(double driverAvailabilityRate) { this.driverAvailabilityRate = driverAvailabilityRate; return this; }
        public Builder totalDistanceTraveled(double totalDistanceTraveled) { this.totalDistanceTraveled = totalDistanceTraveled; return this; }
        public Builder totalFuelConsumed(double totalFuelConsumed) { this.totalFuelConsumed = totalFuelConsumed; return this; }
        public Builder averageFuelEfficiency(double averageFuelEfficiency) { this.averageFuelEfficiency = averageFuelEfficiency; return this; }
        public Builder totalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
        public Builder costPerKilometer(double costPerKilometer) { this.costPerKilometer = costPerKilometer; return this; }
        public Builder revenuePerKilometer(double revenuePerKilometer) { this.revenuePerKilometer = revenuePerKilometer; return this; }
        public Builder averageSafetyScore(double averageSafetyScore) { this.averageSafetyScore = averageSafetyScore; return this; }
        public Builder totalAccidents(int totalAccidents) { this.totalAccidents = totalAccidents; return this; }
        public Builder totalCitations(int totalCitations) { this.totalCitations = totalCitations; return this; }
        public Builder incidentsPerMillionKm(double incidentsPerMillionKm) { this.incidentsPerMillionKm = incidentsPerMillionKm; return this; }
        public Builder averageMaintenanceCost(double averageMaintenanceCost) { this.averageMaintenanceCost = averageMaintenanceCost; return this; }
        public Builder maintenanceCostPerKm(double maintenanceCostPerKm) { this.maintenanceCostPerKm = maintenanceCostPerKm; return this; }
        public Builder vehiclesDueForMaintenance(int vehiclesDueForMaintenance) { this.vehiclesDueForMaintenance = vehiclesDueForMaintenance; return this; }
        public Builder expiredLicenses(int expiredLicenses) { this.expiredLicenses = expiredLicenses; return this; }
        public Builder expiredInsurance(int expiredInsurance) { this.expiredInsurance = expiredInsurance; return this; }
        public Builder expiredDocuments(int expiredDocuments) { this.expiredDocuments = expiredDocuments; return this; }
        public Builder calculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; return this; }

        public FleetAnalytics build() {
            return new FleetAnalytics(this);
        }
    }

    @Override
    public String toString() {
        return "FleetAnalytics{" +
                "totalVehicles=" + totalVehicles +
                ", utilizationRate=" + utilizationRate + "%" +
                ", averageFuelEfficiency=" + averageFuelEfficiency +
                ", calculatedAt=" + calculatedAt +
                '}';
    }
}