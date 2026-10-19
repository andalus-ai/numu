package tech.kayys.numou.fms.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.fms.domain.identifier.GeofenceId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Geofence aggregate root.
 * Defines geographic boundaries for fleet monitoring and alerts.
 */
public final class Geofence extends AbstractAggregateRoot<GeofenceId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private String type; // CIRCLE, POLYGON, RECTANGLE, ROUTE
    private double centerLatitude;
    private double centerLongitude;
    private double radius; // For circle type
    private List<GeofencePoint> points; // For polygon type
    private double minLatitude;
    private double minLongitude;
    private double maxLatitude;
    private double maxLongitude;
    private List<String> vehicleIds;
    private List<String> driverIds;
    private List<GeofenceAlert> alerts;
    private List<GeofenceEvent> events;
    private boolean active;
    private String createdBy;
    private String notes;

    private Geofence(GeofenceId id) {
        super(id);
        this.points = new ArrayList<>();
        this.vehicleIds = new ArrayList<>();
        this.driverIds = new ArrayList<>();
        this.alerts = new ArrayList<>();
        this.events = new ArrayList<>();
        this.active = true;
    }

    private Geofence() {
        super();
    }

    /**
     * Factory method to create a new geofence.
     */
    public static Geofence create(
            GeofenceId id,
            String name,
            String type,
            double centerLatitude,
            double centerLongitude,
            double radius) {
        Geofence geofence = new Geofence(id);
        geofence.name = name;
        geofence.type = type;
        geofence.centerLatitude = centerLatitude;
        geofence.centerLongitude = centerLongitude;
        geofence.radius = radius;
        return geofence;
    }

    /**
     * Creates a polygon geofence.
     */
    public static Geofence createPolygon(
            GeofenceId id,
            String name,
            List<GeofencePoint> points) {
        Geofence geofence = new Geofence(id);
        geofence.name = name;
        geofence.type = "POLYGON";
        geofence.points = new ArrayList<>(points);
        geofence.calculateBounds();
        return geofence;
    }

    private void calculateBounds() {
        if (points.isEmpty()) return;
        
        double minLat = Double.MAX_VALUE, maxLat = Double.MIN_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = Double.MIN_VALUE;
        
        for (GeofencePoint point : points) {
            minLat = Math.min(minLat, point.getLatitude());
            maxLat = Math.max(maxLat, point.getLatitude());
            minLon = Math.min(minLon, point.getLongitude());
            maxLon = Math.max(maxLon, point.getLongitude());
        }
        
        this.minLatitude = minLat;
        this.maxLatitude = maxLat;
        this.minLongitude = minLon;
        this.maxLongitude = maxLon;
    }

    /**
     * Adds a vehicle to the geofence.
     */
    public void addVehicle(String vehicleId) {
        if (!vehicleIds.contains(vehicleId)) {
            vehicleIds.add(vehicleId);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Adds a driver to the geofence.
     */
    public void addDriver(String driverId) {
        if (!driverIds.contains(driverId)) {
            driverIds.add(driverId);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Checks if a point is inside the geofence.
     */
    public boolean isInside(double latitude, double longitude) {
        if ("CIRCLE".equals(type)) {
            return isInsideCircle(latitude, longitude);
        } else if ("POLYGON".equals(type)) {
            return isInsidePolygon(latitude, longitude);
        }
        return false;
    }

    private boolean isInsideCircle(double latitude, double longitude) {
        double R = 6371; // Earth's radius in km
        double dLat = Math.toRadians(latitude - centerLatitude);
        double dLon = Math.toRadians(longitude - centerLongitude);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(centerLatitude)) * Math.cos(Math.toRadians(latitude)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double distance = R * c;
        return distance <= radius;
    }

    private boolean isInsidePolygon(double latitude, double longitude) {
        if (points.size() < 3) return false;
        
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            GeofencePoint pi = points.get(i);
            GeofencePoint pj = points.get(j);
            
            boolean intersect = ((pi.getLatitude() > latitude) != (pj.getLatitude() > latitude)) &&
                (longitude < (pj.getLongitude() - pi.getLongitude()) * 
                (latitude - pi.getLatitude()) / (pj.getLatitude() - pi.getLatitude()) + pi.getLongitude());
            
            if (intersect) inside = !inside;
        }
        return inside;
    }

    /**
     * Records a geofence event.
     */
    public void recordEvent(String vehicleId, String eventType, double latitude, double longitude) {
        GeofenceEvent event = new GeofenceEvent(
            java.util.UUID.randomUUID().toString(),
            vehicleId,
            eventType,
            latitude,
            longitude,
            Instant.now()
        );
        events.add(event);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds an alert to the geofence.
     */
    public void addAlert(GeofenceAlert alert) {
        alerts.add(alert);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public double getCenterLatitude() { return centerLatitude; }
    public double getCenterLongitude() { return centerLongitude; }
    public double getRadius() { return radius; }
    public List<GeofencePoint> getPoints() { return Collections.unmodifiableList(points); }
    public double getMinLatitude() { return minLatitude; }
    public double getMinLongitude() { return minLongitude; }
    public double getMaxLatitude() { return maxLatitude; }
    public double getMaxLongitude() { return maxLongitude; }
    public List<String> getVehicleIds() { return Collections.unmodifiableList(vehicleIds); }
    public List<String> getDriverIds() { return Collections.unmodifiableList(driverIds); }
    public List<GeofenceAlert> getAlerts() { return Collections.unmodifiableList(alerts); }
    public List<GeofenceEvent> getEvents() { return Collections.unmodifiableList(events); }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public String getNotes() { return notes; }

    public void setDescription(String description) {
        this.description = description;
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
        return "Geofence{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", active=" + active +
                '}';
    }

    /**
     * Geofence point value object.
     */
    public static final class GeofencePoint {
        private final double latitude;
        private final double longitude;
        private final int order;

        public GeofencePoint(double latitude, double longitude, int order) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.order = order;
        }

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public int getOrder() { return order; }
    }

    /**
     * Geofence alert value object.
     */
    public static final class GeofenceAlert {
        private final String alertId;
        private final String type; // ENTRY, EXIT, SPEED, DWELL
        private final String message;
        private final String severity; // INFO, WARNING, CRITICAL
        private final boolean active;

        public GeofenceAlert(String alertId, String type, String message, String severity, boolean active) {
            this.alertId = alertId;
            this.type = type;
            this.message = message;
            this.severity = severity;
            this.active = active;
        }

        public String getAlertId() { return alertId; }
        public String getType() { return type; }
        public String getMessage() { return message; }
        public String getSeverity() { return severity; }
        public boolean isActive() { return active; }
    }

    /**
     * Geofence event record.
     */
    public static final class GeofenceEvent {
        private final String eventId;
        private final String vehicleId;
        private final String eventType; // ENTER, EXIT, SPEEDING, DWELL
        private final double latitude;
        private final double longitude;
        private final Instant timestamp;

        public GeofenceEvent(String eventId, String vehicleId, String eventType, double latitude, double longitude, Instant timestamp) {
            this.eventId = eventId;
            this.vehicleId = vehicleId;
            this.eventType = eventType;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }

        public String getEventId() { return eventId; }
        public String getVehicleId() { return vehicleId; }
        public String getEventType() { return eventType; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public Instant getTimestamp() { return timestamp; }
    }
}