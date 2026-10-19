package tech.kayys.numou.fms.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.fms.domain.identifier.DriverId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Driver aggregate root.
 * Complete driver profile with compliance tracking.
 */
public final class Driver extends AbstractAggregateRoot<DriverId> {
    
    private static final long serialVersionUID = 1L;
    
    // Personal Information
    private String employeeNumber;
    private String firstName;
    private String lastName;
    private String middleName;
    private String gender;
    private LocalDate dateOfBirth;
    private String nationality;
    private String personalEmail;
    private String workEmail;
    private String personalPhone;
    private String workPhone;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    
    // License Information
    private String licenseNumber;
    private String licenseClass; // A, B, C, D, E
    private String licenseType; // PASSENGER, COMMERCIAL, HAZMAT
    private LocalDate licenseIssueDate;
    private LocalDate licenseExpiryDate;
    private String licenseState;
    private List<String> endorsements;
    private List<String> restrictions;
    
    // Employment Information
    private String companyId;
    private String departmentId;
    private LocalDate hireDate;
    private String employmentType; // PERMANENT, CONTRACT, FREELANCE
    private DriverStatus status;
    private String managerId;
    private String managerName;
    private double baseSalary;
    private double overtimeRate;
    private double bonusEligibility;
    
    // Performance & Safety
    private List<DriverIncident> incidents;
    private List<DriverTraining> trainings;
    private List<DriverAssignment> assignments;
    private double safetyScore;
    private double performanceScore;
    private int totalTrips;
    private double totalDistance;
    private double totalHours;
    private int citations;
    private int accidents;
    private double onTimeDeliveryRate;
    
    // Availability & Schedule
    private String shiftPattern;
    private String preferredRoute;
    private double maxDailyHours;
    private double maxWeeklyHours;
    private double maxMonthlyHours;
    private boolean available;
    private String currentVehicleId;
    private String currentLocation;
    private Instant lastLogin;
    
    // Documents
    private List<DriverDocument> documents;
    
    // Metadata
    private String createdBy;
    private String updatedBy;
    private boolean active;

    private Driver(DriverId id) {
        super(id);
        this.endorsements = new ArrayList<>();
        this.restrictions = new ArrayList<>();
        this.incidents = new ArrayList<>();
        this.trainings = new ArrayList<>();
        this.assignments = new ArrayList<>();
        this.documents = new ArrayList<>();
        this.status = DriverStatus.AVAILABLE;
        this.active = true;
        this.available = true;
        this.safetyScore = 100.0;
        this.performanceScore = 100.0;
        this.onTimeDeliveryRate = 100.0;
    }

    private Driver() {
        super();
    }

    /**
     * Factory method to create a new driver.
     */
    public static Driver create(
            DriverId id,
            String employeeNumber,
            String firstName,
            String lastName,
            String licenseNumber,
            String licenseClass,
            LocalDate hireDate) {
        Driver driver = new Driver(id);
        driver.employeeNumber = employeeNumber;
        driver.firstName = firstName;
        driver.lastName = lastName;
        driver.licenseNumber = licenseNumber;
        driver.licenseClass = licenseClass;
        driver.hireDate = hireDate;
        return driver;
    }

    /**
     * Updates driver status.
     */
    public void updateStatus(DriverStatus newStatus, String reason) {
        this.status = newStatus;
        this.available = newStatus == DriverStatus.AVAILABLE || newStatus == DriverStatus.ASSIGNED;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Assigns vehicle to driver.
     */
    public void assignVehicle(String vehicleId) {
        this.currentVehicleId = vehicleId;
        this.status = DriverStatus.ASSIGNED;
        this.available = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Unassigns vehicle from driver.
     */
    public void unassignVehicle() {
        this.currentVehicleId = null;
        this.status = DriverStatus.AVAILABLE;
        this.available = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records an incident.
     */
    public void recordIncident(DriverIncident incident) {
        incidents.add(incident);
        calculateSafetyScore();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records training completion.
     */
    public void recordTraining(DriverTraining training) {
        trainings.add(training);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records a trip.
     */
    public void recordTrip(double distance, double hours) {
        this.totalTrips++;
        this.totalDistance += distance;
        this.totalHours += hours;
        calculatePerformanceScore();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records on-time delivery.
     */
    public void recordDelivery(boolean onTime) {
        double currentTotal = totalTrips > 0 ? totalTrips : 1;
        double currentRate = (onTimeDeliveryRate * (totalTrips) + (onTime ? 100 : 0)) / (totalTrips + 1);
        this.onTimeDeliveryRate = currentRate;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private void calculateSafetyScore() {
        double baseScore = 100.0;
        double incidentDeduction = incidents.stream()
            .filter(i -> i.getSeverity() == IncidentSeverity.CRITICAL)
            .count() * 15.0;
        double minorDeduction = incidents.stream()
            .filter(i -> i.getSeverity() == IncidentSeverity.MINOR)
            .count() * 5.0;
        this.safetyScore = Math.max(0, baseScore - incidentDeduction - minorDeduction);
    }

    private void calculatePerformanceScore() {
        double baseScore = 100.0;
        // Performance based on on-time delivery rate
        double deliveryBonus = (onTimeDeliveryRate - 80) / 20 * 10;
        this.performanceScore = Math.min(100, Math.max(0, baseScore + deliveryBonus));
    }

    /**
     * Gets the driver's full name.
     */
    public String getFullName() {
        if (middleName != null && !middleName.isEmpty()) {
            return firstName + " " + middleName + " " + lastName;
        }
        return firstName + " " + lastName;
    }

    /**
     * Checks if license is expired.
     */
    public boolean isLicenseExpired() {
        return licenseExpiryDate != null && LocalDate.now().isAfter(licenseExpiryDate);
    }

    /**
     * Gets the days until license expiry.
     */
    public long getDaysUntilLicenseExpiry() {
        if (licenseExpiryDate == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), licenseExpiryDate);
    }

    // Getters
    public String getEmployeeNumber() { return employeeNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getMiddleName() { return middleName; }
    public String getGender() { return gender; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getNationality() { return nationality; }
    public String getPersonalEmail() { return personalEmail; }
    public String getWorkEmail() { return workEmail; }
    public String getPersonalPhone() { return personalPhone; }
    public String getWorkPhone() { return workPhone; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public String getLicenseNumber() { return licenseNumber; }
    public String getLicenseClass() { return licenseClass; }
    public String getLicenseType() { return licenseType; }
    public LocalDate getLicenseIssueDate() { return licenseIssueDate; }
    public LocalDate getLicenseExpiryDate() { return licenseExpiryDate; }
    public String getLicenseState() { return licenseState; }
    public List<String> getEndorsements() { return Collections.unmodifiableList(endorsements); }
    public List<String> getRestrictions() { return Collections.unmodifiableList(restrictions); }
    public String getCompanyId() { return companyId; }
    public String getDepartmentId() { return departmentId; }
    public LocalDate getHireDate() { return hireDate; }
    public String getEmploymentType() { return employmentType; }
    public DriverStatus getStatus() { return status; }
    public String getManagerId() { return managerId; }
    public String getManagerName() { return managerName; }
    public double getBaseSalary() { return baseSalary; }
    public double getOvertimeRate() { return overtimeRate; }
    public double getBonusEligibility() { return bonusEligibility; }
    public List<DriverIncident> getIncidents() { return Collections.unmodifiableList(incidents); }
    public List<DriverTraining> getTrainings() { return Collections.unmodifiableList(trainings); }
    public List<DriverAssignment> getAssignments() { return Collections.unmodifiableList(assignments); }
    public double getSafetyScore() { return safetyScore; }
    public double getPerformanceScore() { return performanceScore; }
    public int getTotalTrips() { return totalTrips; }
    public double getTotalDistance() { return totalDistance; }
    public double getTotalHours() { return totalHours; }
    public int getCitations() { return citations; }
    public int getAccidents() { return accidents; }
    public double getOnTimeDeliveryRate() { return onTimeDeliveryRate; }
    public String getShiftPattern() { return shiftPattern; }
    public String getPreferredRoute() { return preferredRoute; }
    public double getMaxDailyHours() { return maxDailyHours; }
    public double getMaxWeeklyHours() { return maxWeeklyHours; }
    public double getMaxMonthlyHours() { return maxMonthlyHours; }
    public boolean isAvailable() { return available; }
    public String getCurrentVehicleId() { return currentVehicleId; }
    public String getCurrentLocation() { return currentLocation; }
    public Instant getLastLogin() { return lastLogin; }
    public List<DriverDocument> getDocuments() { return Collections.unmodifiableList(documents); }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public boolean isActive() { return active; }

    // Setters
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public void setGender(String gender) { this.gender = gender; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }
    public void setWorkEmail(String workEmail) { this.workEmail = workEmail; }
    public void setPersonalPhone(String personalPhone) { this.personalPhone = personalPhone; }
    public void setWorkPhone(String workPhone) { this.workPhone = workPhone; }
    public void setAddress(String address) { this.address = address; }
    public void setCity(String city) { this.city = city; }
    public void setState(String state) { this.state = state; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public void setCountry(String country) { this.country = country; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public void setLicenseIssueDate(LocalDate licenseIssueDate) { this.licenseIssueDate = licenseIssueDate; }
    public void setLicenseExpiryDate(LocalDate licenseExpiryDate) { this.licenseExpiryDate = licenseExpiryDate; }
    public void setLicenseState(String licenseState) { this.licenseState = licenseState; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public void setManagerId(String managerId) { this.managerId = managerId; }
    public void setManagerName(String managerName) { this.managerName = managerName; }
    public void setBaseSalary(double baseSalary) { this.baseSalary = baseSalary; }
    public void setOvertimeRate(double overtimeRate) { this.overtimeRate = overtimeRate; }
    public void setBonusEligibility(double bonusEligibility) { this.bonusEligibility = bonusEligibility; }
    public void setShiftPattern(String shiftPattern) { this.shiftPattern = shiftPattern; }
    public void setPreferredRoute(String preferredRoute) { this.preferredRoute = preferredRoute; }
    public void setMaxDailyHours(double maxDailyHours) { this.maxDailyHours = maxDailyHours; }
    public void setMaxWeeklyHours(double maxWeeklyHours) { this.maxWeeklyHours = maxWeeklyHours; }
    public void setMaxMonthlyHours(double maxMonthlyHours) { this.maxMonthlyHours = maxMonthlyHours; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public void addEndorsement(String endorsement) {
        if (!endorsements.contains(endorsement)) {
            endorsements.add(endorsement);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    public void addRestriction(String restriction) {
        if (!restrictions.contains(restriction)) {
            restrictions.add(restriction);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    public void addDocument(DriverDocument document) {
        documents.add(document);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "Driver{" +
                "id=" + getId() +
                ", employeeNumber='" + employeeNumber + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", status=" + status +
                ", safetyScore=" + safetyScore +
                '}';
    }

    /**
     * Driver status enum.
     */
    public enum DriverStatus {
        AVAILABLE("Available"),
        ASSIGNED("Assigned"),
        ON_TRIP("On Trip"),
        ON_BREAK("On Break"),
        OFF_DUTY("Off Duty"),
        SUSPENDED("Suspended"),
        TERMINATED("Terminated");

        private final String description;

        DriverStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Incident severity enum.
     */
    public enum IncidentSeverity {
        MINOR("Minor"),
        MODERATE("Moderate"),
        SEVERE("Severe"),
        CRITICAL("Critical");

        private final String description;

        IncidentSeverity(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Driver incident record.
     */
    public static final class DriverIncident {
        private final String incidentId;
        private final String type;
        private final String description;
        private final IncidentSeverity severity;
        private final Instant date;
        private final String location;
        private final String reportingOfficer;
        private final String resolution;

        public DriverIncident(
                String incidentId,
                String type,
                String description,
                IncidentSeverity severity,
                Instant date,
                String location,
                String reportingOfficer,
                String resolution) {
            this.incidentId = incidentId;
            this.type = type;
            this.description = description;
            this.severity = severity;
            this.date = date;
            this.location = location;
            this.reportingOfficer = reportingOfficer;
            this.resolution = resolution;
        }

        public String getIncidentId() { return incidentId; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public IncidentSeverity getSeverity() { return severity; }
        public Instant getDate() { return date; }
        public String getLocation() { return location; }
        public String getReportingOfficer() { return reportingOfficer; }
        public String getResolution() { return resolution; }
    }

    /**
     * Driver training record.
     */
    public static final class DriverTraining {
        private final String trainingId;
        private final String trainingName;
        private final String provider;
        private final Instant completionDate;
        private final Instant expiryDate;
        private final double score;
        private final boolean certified;

        public DriverTraining(
                String trainingId,
                String trainingName,
                String provider,
                Instant completionDate,
                Instant expiryDate,
                double score,
                boolean certified) {
            this.trainingId = trainingId;
            this.trainingName = trainingName;
            this.provider = provider;
            this.completionDate = completionDate;
            this.expiryDate = expiryDate;
            this.score = score;
            this.certified = certified;
        }

        public String getTrainingId() { return trainingId; }
        public String getTrainingName() { return trainingName; }
        public String getProvider() { return provider; }
        public Instant getCompletionDate() { return completionDate; }
        public Instant getExpiryDate() { return expiryDate; }
        public double getScore() { return score; }
        public boolean isCertified() { return certified; }
        public boolean isExpired() {
            return expiryDate != null && Instant.now().isAfter(expiryDate);
        }
    }

    /**
     * Driver assignment record.
     */
    public static final class DriverAssignment {
        private final String assignmentId;
        private final String vehicleId;
        private final String routeId;
        private final Instant startDate;
        private final Instant endDate;
        private final String status;

        public DriverAssignment(
                String assignmentId,
                String vehicleId,
                String routeId,
                Instant startDate,
                Instant endDate,
                String status) {
            this.assignmentId = assignmentId;
            this.vehicleId = vehicleId;
            this.routeId = routeId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.status = status;
        }

        public String getAssignmentId() { return assignmentId; }
        public String getVehicleId() { return vehicleId; }
        public String getRouteId() { return routeId; }
        public Instant getStartDate() { return startDate; }
        public Instant getEndDate() { return endDate; }
        public String getStatus() { return status; }
    }

    /**
     * Driver document record.
     */
    public static final class DriverDocument {
        private final String documentId;
        private final String documentType;
        private final String documentName;
        private final String fileUrl;
        private final Instant uploadedAt;
        private final String uploadedBy;
        private final Instant expiryDate;

        public DriverDocument(
                String documentId,
                String documentType,
                String documentName,
                String fileUrl,
                Instant uploadedAt,
                String uploadedBy,
                Instant expiryDate) {
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
        public Instant getExpiryDate() { return expiryDate; }
        public boolean isExpired() {
            return expiryDate != null && Instant.now().isAfter(expiryDate);
        }
    }
}