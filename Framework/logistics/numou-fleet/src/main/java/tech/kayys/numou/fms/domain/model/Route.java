package tech.kayys.numou.fms.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.fms.domain.identifier.RouteId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Route aggregate root.
 * Manages route planning and optimization for fleet operations.
 */
public final class Route extends AbstractAggregateRoot<RouteId> {
    
    private static final long serialVersionUID = 1L;
    
    private String routeCode;
    private String name;
    private String description;
    private String startLocation;
    private String endLocation;
    private List<RouteStop> stops;
    private double totalDistance;
    private double estimatedDuration;
    private double actualDuration;
    private String vehicleId;
    private String driverId;
    private List<RouteOptimization> optimizationHistory;
    private RouteStatus status;
    private Instant scheduledStart;
    private Instant scheduledEnd;
    private Instant actualStart;
    private Instant actualEnd;
    private double fuelConsumption;
    private double cost;
    private String currencyCode;
    private List<RouteWaypoint> waypoints;
    private String notes;
    private String createdBy;
    private boolean active;

    private Route(RouteId id) {
        super(id);
        this.stops = new ArrayList<>();
        this.optimizationHistory = new ArrayList<>();
        this.waypoints = new ArrayList<>();
        this.status = RouteStatus.PLANNED;
        this.active = true;
    }

    private Route() {
        super();
    }

    /**
     * Factory method to create a new route.
     */
    public static Route create(
            RouteId id,
            String routeCode,
            String name,
            String startLocation,
            String endLocation) {
        Route route = new Route(id);
        route.routeCode = routeCode;
        route.name = name;
        route.startLocation = startLocation;
        route.endLocation = endLocation;
        return route;
    }

    /**
     * Adds a stop to the route.
     */
    public void addStop(RouteStop stop) {
        stops.add(stop);
        stops.sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));
        recalculateRoute();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Optimizes the route using the selected algorithm.
     */
    public RouteOptimization optimize(OptimizationAlgorithm algorithm) {
        RouteOptimization optimization = new RouteOptimization(
            java.util.UUID.randomUUID().toString(),
            algorithm,
            Instant.now(),
            "Optimizing route"
        );
        
        // Apply the chosen algorithm
        switch (algorithm) {
            case NEAREST_NEIGHBOR -> optimizeNearestNeighbor();
            case GENETIC_ALGORITHM -> optimizeGenetic();
            case DYNAMIC_PROGRAMMING -> optimizeDynamicProgramming();
            case ML_BASED -> optimizeMLBased();
        }
        
        optimization.setResult(totalDistance, estimatedDuration, cost, fuelConsumption);
        optimizationHistory.add(optimization);
        
        recalculateRoute();
        setUpdatedAt(Instant.now());
        incrementVersion();
        return optimization;
    }

    private void optimizeNearestNeighbor() {
        // Nearest Neighbor algorithm for route optimization
        List<RouteStop> optimizedStops = new ArrayList<>();
        if (stops.isEmpty()) return;
        
        RouteStop current = stops.get(0);
        optimizedStops.add(current);
        List<RouteStop> remaining = new ArrayList<>(stops.subList(1, stops.size()));
        
        while (!remaining.isEmpty()) {
            RouteStop nearest = null;
            double minDistance = Double.MAX_VALUE;
            
            for (RouteStop stop : remaining) {
                double distance = calculateDistance(current, stop);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = stop;
                }
            }
            
            if (nearest != null) {
                optimizedStops.add(nearest);
                remaining.remove(nearest);
                current = nearest;
            }
        }
        
        this.stops = optimizedStops;
    }

    private void optimizeGenetic() {
        // Genetic Algorithm implementation
        // In production, use a proper genetic algorithm library
        optimizeNearestNeighbor(); // Placeholder
    }

    private void optimizeDynamicProgramming() {
        // Dynamic Programming implementation
        // In production, use proper DP algorithm
        optimizeNearestNeighbor(); // Placeholder
    }

    private void optimizeMLBased() {
        // ML-based optimization
        // In production, use ML models
        optimizeNearestNeighbor(); // Placeholder
    }

    private double calculateDistance(RouteStop from, RouteStop to) {
        // In production, use GIS API for accurate distance
        // Haversine formula for approximate distance
        double lat1 = from.getLatitude();
        double lon1 = from.getLongitude();
        double lat2 = to.getLatitude();
        double lon2 = to.getLongitude();
        
        double R = 6371; // Earth's radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    private void recalculateRoute() {
        if (stops.isEmpty()) {
            this.totalDistance = 0;
            this.estimatedDuration = 0;
            return;
        }
        
        double distance = 0;
        double duration = 0;
        
        for (int i = 0; i < stops.size() - 1; i++) {
            RouteStop from = stops.get(i);
            RouteStop to = stops.get(i + 1);
            distance += calculateDistance(from, to);
            duration += to.getEstimatedDuration();
        }
        
        this.totalDistance = distance;
        this.estimatedDuration = duration;
        this.fuelConsumption = distance * 0.1; // Placeholder fuel consumption
        this.cost = distance * 0.5; // Placeholder cost per km
    }

    /**
     * Starts the route execution.
     */
    public void startRoute() {
        if (status != RouteStatus.PLANNED && status != RouteStatus.OPTIMIZED) {
            throw new IllegalStateException("Cannot start route in status: " + status);
        }
        this.status = RouteStatus.IN_PROGRESS;
        this.actualStart = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Completes the route.
     */
    public void completeRoute() {
        if (status != RouteStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot complete route in status: " + status);
        }
        this.status = RouteStatus.COMPLETED;
        this.actualEnd = Instant.now();
        this.actualDuration = java.time.Duration.between(actualStart, actualEnd).toMinutes();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Cancels the route.
     */
    public void cancelRoute(String reason) {
        if (status == RouteStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed route");
        }
        this.status = RouteStatus.CANCELLED;
        this.notes = reason;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets the route status summary.
     */
    public RouteSummary getSummary() {
        return new RouteSummary(
            routeCode,
            name,
            status,
            totalDistance,
            estimatedDuration,
            actualDuration,
            fuelConsumption,
            cost,
            stops.size(),
            startLocation,
            endLocation
        );
    }

    // Getters
    public String getRouteCode() { return routeCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStartLocation() { return startLocation; }
    public String getEndLocation() { return endLocation; }
    public List<RouteStop> getStops() { return Collections.unmodifiableList(stops); }
    public double getTotalDistance() { return totalDistance; }
    public double getEstimatedDuration() { return estimatedDuration; }
    public double getActualDuration() { return actualDuration; }
    public String getVehicleId() { return vehicleId; }
    public String getDriverId() { return driverId; }
    public List<RouteOptimization> getOptimizationHistory() { return Collections.unmodifiableList(optimizationHistory); }
    public RouteStatus getStatus() { return status; }
    public Instant getScheduledStart() { return scheduledStart; }
    public Instant getScheduledEnd() { return scheduledEnd; }
    public Instant getActualStart() { return actualStart; }
    public Instant getActualEnd() { return actualEnd; }
    public double getFuelConsumption() { return fuelConsumption; }
    public double getCost() { return cost; }
    public String getCurrencyCode() { return currencyCode; }
    public List<RouteWaypoint> getWaypoints() { return Collections.unmodifiableList(waypoints); }
    public String getNotes() { return notes; }
    public String getCreatedBy() { return createdBy; }
    public boolean isActive() { return active; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setScheduledStart(Instant scheduledStart) {
        this.scheduledStart = scheduledStart;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setScheduledEnd(Instant scheduledEnd) {
        this.scheduledEnd = scheduledEnd;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "Route{" +
                "id=" + getId() +
                ", routeCode='" + routeCode + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", totalDistance=" + totalDistance +
                '}';
    }

    /**
     * Route status enum.
     */
    public enum RouteStatus {
        PLANNED("Planned"),
        OPTIMIZED("Optimized"),
        ASSIGNED("Assigned"),
        IN_PROGRESS("In Progress"),
        COMPLETED("Completed"),
        CANCELLED("Cancelled");

        private final String description;

        RouteStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Optimization algorithm enum.
     */
    public enum OptimizationAlgorithm {
        NEAREST_NEIGHBOR("Nearest Neighbor"),
        GENETIC_ALGORITHM("Genetic Algorithm"),
        DYNAMIC_PROGRAMMING("Dynamic Programming"),
        ML_BASED("ML-Based Optimization");

        private final String description;

        OptimizationAlgorithm(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Route stop value object.
     */
    public static final class RouteStop {
        private final String stopId;
        private final String locationName;
        private final String address;
        private final double latitude;
        private final double longitude;
        private final int sequence;
        private final double estimatedDuration; // minutes
        private final String type; // PICKUP, DROPOFF, CHECKPOINT, REST
        private final String notes;

        public RouteStop(
                String stopId,
                String locationName,
                String address,
                double latitude,
                double longitude,
                int sequence,
                double estimatedDuration,
                String type,
                String notes) {
            this.stopId = stopId;
            this.locationName = locationName;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
            this.sequence = sequence;
            this.estimatedDuration = estimatedDuration;
            this.type = type;
            this.notes = notes;
        }

        public String getStopId() { return stopId; }
        public String getLocationName() { return locationName; }
        public String getAddress() { return address; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public int getSequence() { return sequence; }
        public double getEstimatedDuration() { return estimatedDuration; }
        public String getType() { return type; }
        public String getNotes() { return notes; }
    }

    /**
     * Route optimization value object.
     */
    public static final class RouteOptimization {
        private final String optimizationId;
        private final OptimizationAlgorithm algorithm;
        private final Instant timestamp;
        private final String description;
        private double optimizedDistance;
        private double optimizedDuration;
        private double optimizedCost;
        private double optimizedFuelConsumption;

        public RouteOptimization(
                String optimizationId,
                OptimizationAlgorithm algorithm,
                Instant timestamp,
                String description) {
            this.optimizationId = optimizationId;
            this.algorithm = algorithm;
            this.timestamp = timestamp;
            this.description = description;
        }

        public void setResult(double distance, double duration, double cost, double fuel) {
            this.optimizedDistance = distance;
            this.optimizedDuration = duration;
            this.optimizedCost = cost;
            this.optimizedFuelConsumption = fuel;
        }

        public String getOptimizationId() { return optimizationId; }
        public OptimizationAlgorithm getAlgorithm() { return algorithm; }
        public Instant getTimestamp() { return timestamp; }
        public String getDescription() { return description; }
        public double getOptimizedDistance() { return optimizedDistance; }
        public double getOptimizedDuration() { return optimizedDuration; }
        public double getOptimizedCost() { return optimizedCost; }
        public double getOptimizedFuelConsumption() { return optimizedFuelConsumption; }
    }

    /**
     * Route waypoint value object.
     */
    public static final class RouteWaypoint {
        private final double latitude;
        private final double longitude;
        private final int order;
        private final String description;

        public RouteWaypoint(double latitude, double longitude, int order, String description) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.order = order;
            this.description = description;
        }

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public int getOrder() { return order; }
        public String getDescription() { return description; }
    }

    /**
     * Route summary record.
     */
    public record RouteSummary(
        String routeCode,
        String name,
        RouteStatus status,
        double totalDistance,
        double estimatedDuration,
        double actualDuration,
        double fuelConsumption,
        double cost,
        int numberOfStops,
        String startLocation,
        String endLocation
    ) {}
}