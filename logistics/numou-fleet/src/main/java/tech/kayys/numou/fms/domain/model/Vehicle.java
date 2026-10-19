package tech.kayys.numou.fms.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.fms.domain.identifier.VehicleId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vehicle aggregate root.
 * Complete vehicle profile with asset management.
 */
public final class Vehicle extends AbstractAggregateRoot<VehicleId> {
    
    private static final long serialVersionUID = 1L;
    
    // Vehicle Identification
    private String registrationNumber;
    private String vin;
    private String make;
    private String model;
    private int year;
    private String color;
    private String bodyType; // SEDAN, SUV, TRUCK, VAN, BUS, MOTORCYCLE
    private String fuelType; // PETROL, DIESEL, ELECTRIC, HYBRID, LPG, CNG
    private String transmission; // MANUAL, AUTOMATIC, CVT
    private String engineSize;
    private String horsepower;
    private String licensePlate;
    private String licensePlateState;
    private String licensePlateExpiryDate;
    
    // Ownership & Registration
    private String ownerId;
    private String ownerType; // COMPANY, INDIVIDUAL, LEASE
    private LocalDate purchaseDate;
    private double purchasePrice;
    private String currencyCode;
    private LocalDate registrationDate;
    private String insurancePolicyNumber;
    private String insuranceProvider;
    private LocalDate insuranceStartDate;
    private LocalDate insuranceEndDate;
    private String insuranceType; // COMPREHENSIVE, THIRD_PARTY, FIRE_THEFT
    
    // Vehicle Specifications
    private double weight; // Empty weight in kg
    private double grossWeight; // Maximum weight in kg
    private double length;
    private double width;
    private double height;
    private int seatingCapacity;
    private double fuelTankCapacity;
    private double cargoVolume;
    private double maxPayload;
    private double towingCapacity;
    private double fuelEfficiency; // km per liter
    
    // Status & Usage
    private VehicleStatus status;
    private String currentDriverId;
    private String currentLocation;
    private double currentLatitude;
    private double currentLongitude;
    private double totalDistanceTraveled;
    private double totalFuelConsumed;
    private int totalTrips;
    private double averageFuelEfficiency;
    private double currentMileage;
    private double lastMaintenanceMileage;
    private double nextMaintenanceMileage;
    private String assignedTo;
    private String assignedToName;
    
    // Maintenance & History
    private List<VehicleMaintenance> maintenanceHistory;
    private List<VehicleTrip> trips;
    private List<VehicleDocument> documents;
    private List<VehicleAlert> alerts;
    
    // Telematics
    private String gpsDeviceId;
    private String telematicsProvider;
    private boolean realTimeTracking;
    private double lastOdometerReading;
    private Instant lastLocationUpdate;
    
    // Metadata
    private String createdBy;
    private String updatedBy;
    private boolean active;

    private Vehicle(VehicleId id) {
        super(id);
        this.maintenanceHistory = new ArrayList<>();
        this.trips = new ArrayList<>();
        this.documents = new ArrayList<>();
        this.alerts = new ArrayList<>();
        this.status = VehicleStatus.AVAILABLE;
        this.active = true;
        this.realTimeTracking = true;
    }

    private Vehicle() {
        super();
    }

    /**
     * Factory method to create a new vehicle.
     */
    public static Vehicle create(
            VehicleId id,
            String registrationNumber,
            String vin,
            String make,
            String model,
            int year,
            String licensePlate) {
        Vehicle vehicle = new Vehicle(id);
        vehicle.registrationNumber = registrationNumber;
        vehicle.vin = vin;
        vehicle.make = make;
        vehicle.model = model;
        vehicle.year = year;
        vehicle.licensePlate = licensePlate;
        return vehicle;
    }

    /**
     * Updates vehicle status.
     */
    public void updateStatus(VehicleStatus newStatus, String reason) {
        this.status = newStatus;
        addAlert("STATUS_CHANGE", "Status changed to " + newStatus + ": " + reason);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Assigns a driver to the vehicle.
     */
    public void assignDriver(String driverId, String driverName) {
        this.currentDriverId = driverId;
        this.assignedToName = driverName;
        this.status = VehicleStatus.ASSIGNED;
        addAlert("DRIVER_ASSIGNED", "Driver assigned: " + driverName);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates vehicle location.
     */
    public void updateLocation(double latitude, double longitude, String location) {
        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
        this.currentLocation = location;
        this.lastLocationUpdate = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records a trip.
     */
    public void recordTrip(VehicleTrip trip) {
        trips.add(trip);
        this.totalTrips++;
        this.totalDistanceTraveled += trip.getDistance();
        this.totalFuelConsumed += trip.getFuelConsumed();
        this.currentMileage = trip.getEndOdometer();
        this.averageFuelEfficiency = totalDistanceTraveled / totalFuelConsumed;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records maintenance.
     */
    public void recordMaintenance(VehicleMaintenance maintenance) {
        maintenanceHistory.add(maintenance);
        this.lastMaintenanceMileage = maintenance.getMileage();
        this.nextMaintenanceMileage = maintenance.getMileage() + maintenance.getNextMaintenanceInterval();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a document to the vehicle.
     */
    public void addDocument(VehicleDocument document) {
        documents.add(document);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds an alert to the vehicle.
     */
    public void addAlert(String type, String description) {
        VehicleAlert alert = new VehicleAlert(
            java.util.UUID.randomUUID().toString(),
            type,
            description,
            AlertSeverity.WARNING,
            Instant.now()
        );
        alerts.add(alert);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Checks if maintenance is due.
     */
    public boolean isMaintenanceDue() {
        return currentMileage >= nextMaintenanceMileage;
    }

    /**
     * Gets the days until maintenance.
     */
    public long getDaysUntilMaintenance() {
        if (nextMaintenanceMileage <= currentMileage) {
            return 0;
        }
        // Assuming average daily mileage
        double dailyMileage = 100; // Placeholder
        return (long) ((nextMaintenanceMileage - currentMileage) / dailyMileage);
    }

    /**
     * Gets the vehicle utilization rate.
     */
    public double getUtilizationRate() {
        if (trips.isEmpty()) {
            return 0.0;
        }
        long totalHours = trips.stream()
            .mapToLong(VehicleTrip::getDurationHours)
            .sum();
        long totalDays = java.time.Duration.between(
            trips.get(0).getStartTime(),
            Instant.now()
        ).toDays() + 1;
        return (double) totalHours / (totalDays * 24) * 100;
    }

    // Getters
    public String getRegistrationNumber() { return registrationNumber; }
    public String getVin() { return vin; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public int getYear() { return year; }
    public String getColor() { return color; }
    public String getBodyType() { return bodyType; }
    public String getFuelType() { return fuelType; }
    public String getTransmission() { return transmission; }
    public String getEngineSize() { return engineSize; }
    public String getHorsepower() { return horsepower; }
    public String getLicensePlate() { return licensePlate; }
    public String getLicensePlateState() { return licensePlateState; }
    public String getLicensePlateExpiryDate() { return licensePlateExpiryDate; }
    public String getOwnerId() { return ownerId; }
    public String getOwnerType() { return ownerType; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public double getPurchasePrice() { return purchasePrice; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getRegistrationDate() { return registrationDate; }
    public String getInsurancePolicyNumber() { return insurancePolicyNumber; }
    public String getInsuranceProvider() { return insuranceProvider; }
    public LocalDate getInsuranceStartDate() { return insuranceStartDate; }
    public LocalDate getInsuranceEndDate() { return insuranceEndDate; }
    public String getInsuranceType() { return insuranceType; }
    public double getWeight() { return weight; }
    public double getGrossWeight() { return grossWeight; }
    public double getLength() { return length; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public int getSeatingCapacity() { return seatingCapacity; }
    public double getFuelTankCapacity() { return fuelTankCapacity; }
    public double getCargoVolume() { return cargoVolume; }
    public double getMaxPayload() { return maxPayload; }
    public double getTowingCapacity() { return towingCapacity; }
    public double getFuelEfficiency() { return fuelEfficiency; }
    public VehicleStatus getStatus() { return status; }
    public String getCurrentDriverId() { return currentDriverId; }
    public String getCurrentLocation() { return currentLocation; }
    public double getCurrentLatitude() { return currentLatitude; }
    public double getCurrentLongitude() { return currentLongitude; }
    public double getTotalDistanceTraveled() { return totalDistanceTraveled; }
    public double getTotalFuelConsumed() { return totalFuelConsumed; }
    public int getTotalTrips() { return totalTrips; }
    public double getAverageFuelEfficiency() { return averageFuelEfficiency; }
    public double getCurrentMileage() { return currentMileage; }
    public double getLastMaintenanceMileage() { return lastMaintenanceMileage; }
    public double getNextMaintenanceMileage() { return nextMaintenanceMileage; }
    public String getAssignedTo() { return assignedTo; }
    public String getAssignedToName() { return assignedToName; }
    public List<VehicleMaintenance> getMaintenanceHistory() { return Collections.unmodifiableList(maintenanceHistory); }
    public List<VehicleTrip> getTrips() { return Collections.unmodifiableList(trips); }
    public List<VehicleDocument> getDocuments() { return Collections.unmodifiableList(documents); }
    public List<VehicleAlert> getAlerts() { return Collections.unmodifiableList(alerts); }
    public String getGpsDeviceId() { return gpsDeviceId; }
    public String getTelematicsProvider() { return telematicsProvider; }
    public boolean isRealTimeTracking() { return realTimeTracking; }
    public double getLastOdometerReading() { return lastOdometerReading; }
    public Instant getLastLocationUpdate() { return lastLocationUpdate; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public boolean isActive() { return active; }

    // Setters
    public void setColor(String color) { this.color = color; }
    public void setBodyType(String bodyType) { this.bodyType = bodyType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }
    public void setTransmission(String transmission) { this.transmission = transmission; }
    public void setEngineSize(String engineSize) { this.engineSize = engineSize; }
    public void setHorsepower(String horsepower) { this.horsepower = horsepower; }
    public void setLicensePlateState(String licensePlateState) { this.licensePlateState = licensePlateState; }
    public void setLicensePlateExpiryDate(String licensePlateExpiryDate) { this.licensePlateExpiryDate = licensePlateExpiryDate; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public void setPurchasePrice(double purchasePrice) { this.purchasePrice = purchasePrice; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public void setRegistrationDate(LocalDate registrationDate) { this.registrationDate = registrationDate; }
    public void setInsurancePolicyNumber(String insurancePolicyNumber) { this.insurancePolicyNumber = insurancePolicyNumber; }
    public void setInsuranceProvider(String insuranceProvider) { this.insuranceProvider = insuranceProvider; }
    public void setInsuranceStartDate(LocalDate insuranceStartDate) { this.insuranceStartDate = insuranceStartDate; }
    public void setInsuranceEndDate(LocalDate insuranceEndDate) { this.insuranceEndDate = insuranceEndDate; }
    public void setInsuranceType(String insuranceType) { this.insuranceType = insuranceType; }
    public void setWeight(double weight) { this.weight = weight; }
    public void setGrossWeight(double grossWeight) { this.grossWeight = grossWeight; }
    public void setLength(double length) { this.length = length; }
    public void setWidth(double width) { this.width = width; }
    public void setHeight(double height) { this.height = height; }
    public void setSeatingCapacity(int seatingCapacity) { this.seatingCapacity = seatingCapacity; }
    public void setFuelTankCapacity(double fuelTankCapacity) { this.fuelTankCapacity = fuelTankCapacity; }
    public void setCargoVolume(double cargoVolume) { this.cargoVolume = cargoVolume; }
    public void setMaxPayload(double maxPayload) { this.maxPayload = maxPayload; }
    public void setTowingCapacity(double towingCapacity) { this.towingCapacity = towingCapacity; }
    public void setFuelEfficiency(double fuelEfficiency) { this.fuelEfficiency = fuelEfficiency; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }
    public void setGpsDeviceId(String gpsDeviceId) { this.gpsDeviceId = gpsDeviceId; }
    public void setTelematicsProvider(String telematicsProvider) { this.telematicsProvider = telematicsProvider; }
    public void setRealTimeTracking(boolean realTimeTracking) { this.realTimeTracking = realTimeTracking; }
    public void setLastOdometerReading(double lastOdometerReading) { this.lastOdometerReading = lastOdometerReading; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + getId() +
                ", registrationNumber='" + registrationNumber + '\'' +
                ", make='" + make + '\'' +
                ", model='" + model + '\'' +
                ", status=" + status +
                ", mileage=" + currentMileage +
                '}';
    }

    /**
     * Vehicle status enum.
     */
    public enum VehicleStatus {
        AVAILABLE("Available"),
        ASSIGNED("Assigned"),
        IN_TRANSIT("In Transit"),
        MAINTENANCE("Maintenance"),
        OUT_OF_SERVICE("Out of Service"),
        RETIRED("Retired");

        private final String description;

        VehicleStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Alert severity enum.
     */
    public enum AlertSeverity {
        INFO("Info"),
        WARNING("Warning"),
        CRITICAL("Critical");

        private final String description;

        AlertSeverity(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Vehicle maintenance record.
     */
    public static final class VehicleMaintenance {
        private final String maintenanceId;
        private final String type;
        private final String description;
        private final double mileage;
        private final double cost;
        private final Instant date;
        private final String performedBy;
        private final double nextMaintenanceInterval;
        private final String status;

        public VehicleMaintenance(
                String maintenanceId,
                String type,
                String description,
                double mileage,
                double cost,
                Instant date,
                String performedBy,
                double nextMaintenanceInterval,
                String status) {
            this.maintenanceId = maintenanceId;
            this.type = type;
            this.description = description;
            this.mileage = mileage;
            this.cost = cost;
            this.date = date;
            this.performedBy = performedBy;
            this.nextMaintenanceInterval = nextMaintenanceInterval;
            this.status = status;
        }

        public String getMaintenanceId() { return maintenanceId; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public double getMileage() { return mileage; }
        public double getCost() { return cost; }
        public Instant getDate() { return date; }
        public String getPerformedBy() { return performedBy; }
        public double getNextMaintenanceInterval() { return nextMaintenanceInterval; }
        public String getStatus() { return status; }
    }

    /**
     * Vehicle trip record.
     */
    public static final class VehicleTrip {
        private final String tripId;
        private final String driverId;
        private final String driverName;
        private final String startLocation;
        private final String endLocation;
        private final Instant startTime;
        private final Instant endTime;
        private final double distance;
        private final double fuelConsumed;
        private final double startOdometer;
        private final double endOdometer;
        private final double averageSpeed;
        private final String purpose;

        public VehicleTrip(
                String tripId,
                String driverId,
                String driverName,
                String startLocation,
                String endLocation,
                Instant startTime,
                Instant endTime,
                double distance,
                double fuelConsumed,
                double startOdometer,
                double endOdometer,
                double averageSpeed,
                String purpose) {
            this.tripId = tripId;
            this.driverId = driverId;
            this.driverName = driverName;
            this.startLocation = startLocation;
            this.endLocation = endLocation;
            this.startTime = startTime;
            this.endTime = endTime;
            this.distance = distance;
            this.fuelConsumed = fuelConsumed;
            this.startOdometer = startOdometer;
            this.endOdometer = endOdometer;
            this.averageSpeed = averageSpeed;
            this.purpose = purpose;
        }

        public String getTripId() { return tripId; }
        public String getDriverId() { return driverId; }
        public String getDriverName() { return driverName; }
        public String getStartLocation() { return startLocation; }
        public String getEndLocation() { return endLocation; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public double getDistance() { return distance; }
        public double getFuelConsumed() { return fuelConsumed; }
        public double getStartOdometer() { return startOdometer; }
        public double getEndOdometer() { return endOdometer; }
        public double getAverageSpeed() { return averageSpeed; }
        public String getPurpose() { return purpose; }

        public long getDurationHours() {
            return java.time.Duration.between(startTime, endTime).toHours();
        }

        public double getFuelEfficiency() {
            return fuelConsumed > 0 ? distance / fuelConsumed : 0;
        }
    }

    /**
     * Vehicle document record.
     */
    public static final class VehicleDocument {
        private final String documentId;
        private final String documentType;
        private final String documentName;
        private final String fileUrl;
        private final Instant uploadedAt;
        private final String uploadedBy;
        private final String expiryDate;

        public VehicleDocument(
                String documentId,
                String documentType,
                String documentName,
                String fileUrl,
                Instant uploadedAt,
                String uploadedBy,
                String expiryDate) {
            this.documentId = documentId;
            this.documentType = documentType;
            this.documentName = documentName;
            this.fileUrl = fileUrl;
            this.uploadedAt = uploadedAt;
            this.uploadedBy = uploadedBy;
            this.expiryDate = expiryDate;
        }

        public String getDocumentId() { return documentId; }
        public String getDocumentType() { return documentType; }
        public String getDocumentName() { return documentName; }
        public String getFileUrl() { return fileUrl; }
        public Instant getUploadedAt() { return uploadedAt; }
        public String getUploadedBy() { return uploadedBy; }
        public String getExpiryDate() { return expiryDate; }
    }

    /**
     * Vehicle alert record.
     */
    public static final class VehicleAlert {
        private final String alertId;
        private final String type;
        private final String description;
        private final AlertSeverity severity;
        private final Instant timestamp;

        public VehicleAlert(String alertId, String type, String description, AlertSeverity severity, Instant timestamp) {
            this.alertId = alertId;
            this.type = type;
            this.description = description;
            this.severity = severity;
            this.timestamp = timestamp;
        }

        public String getAlertId() { return alertId; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public AlertSeverity getSeverity() { return severity; }
        public Instant getTimestamp() { return timestamp; }
    }
}