package tech.kayys.syirkah.hris.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.hris.domain.identifier.PerformanceReviewId;
import tech.kayys.syirkah.hris.domain.valueobject.ReviewStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Performance review aggregate root.
 * Represents an employee's performance evaluation.
 */
public final class PerformanceReview extends AbstractAggregateRoot<PerformanceReviewId> {

    private static final long serialVersionUID = 1L;

    private String employeeId;
    private String reviewerId;
    private String reviewPeriod;
    private LocalDate reviewDate;
    private ReviewStatus status;
    private List<ReviewCriteria> criteria;
    private String overallRating;
    private String strengths;
    private String areasForImprovement;
    private String goals;
    private String reviewerComments;
    private String employeeComments;
    private String nextReviewDate;
    private boolean completed;
    private boolean acknowledged;
    private String acknowledgedBy;
    private Instant acknowledgedAt;

    private PerformanceReview(PerformanceReviewId id) {
        super();
        this.criteria = new ArrayList<>();
        this.status = ReviewStatus.SCHEDULED;
        this.completed = false;
        this.acknowledged = false;
    }

    /**
     * Factory method to create a new performance review.
     */
    public static PerformanceReview create(
            PerformanceReviewId id,
            String employeeId,
            String reviewerId,
            String reviewPeriod,
            LocalDate reviewDate) {
        PerformanceReview review = new PerformanceReview(id);
        review.employeeId = employeeId;
        review.reviewerId = reviewerId;
        review.reviewPeriod = reviewPeriod;
        review.reviewDate = reviewDate;
        return review;
    }

    @Override
    public PerformanceReviewId id() {
        return null;
    }

    /**
     * Adds a review criteria.
     */
    public void addCriteria(ReviewCriteria criterion) {
        if (status == ReviewStatus.COMPLETED || status == ReviewStatus.APPROVED) {
            throw new IllegalStateException("Cannot modify completed review");
        }
        criteria.add(criterion);
    }

    /**
     * Starts the review process.
     */
    public void startReview() {
        if (status != ReviewStatus.SCHEDULED) {
            throw new IllegalStateException("Cannot start review in status: " + status);
        }
        this.status = ReviewStatus.IN_PROGRESS;
    }

    /**
     * Submits the review for approval.
     */
    public void submitForApproval() {
        if (status != ReviewStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot submit review in status: " + status);
        }
        if (criteria.isEmpty()) {
            throw new IllegalStateException("Review must have at least one criteria");
        }
        this.status = ReviewStatus.PENDING_APPROVAL;
    }

    /**
     * Approves the review.
     */
    public void approve() {
        if (status != ReviewStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Cannot approve review in status: " + status);
        }
        this.status = ReviewStatus.APPROVED;
        this.completed = true;
    }

    /**
     * Completes the review without formal approval.
     */
    public void complete() {
        if (status != ReviewStatus.IN_PROGRESS && status != ReviewStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Cannot complete review in status: " + status);
        }
        this.status = ReviewStatus.COMPLETED;
        this.completed = true;
    }

    /**
     * Cancels the review.
     */
    public void cancel(String reason) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Cannot cancel completed review");
        }
        this.status = ReviewStatus.CANCELLED;
        this.completed = false;
    }

    /**
     * Acknowledges the review by the employee.
     */
    public void acknowledge(String acknowledgedBy) {
        if (!completed && status != ReviewStatus.APPROVED) {
            throw new IllegalStateException("Cannot acknowledge incomplete review");
        }
        this.acknowledged = true;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = Instant.now();
    }

    /**
     * Calculates the overall rating.
     */
    public double calculateOverallRating() {
        if (criteria.isEmpty()) {
            return 0.0;
        }
        return criteria.stream()
            .mapToDouble(ReviewCriteria::getRating)
            .average()
            .orElse(0.0);
    }

    /**
     * Gets the overall rating as a string.
     */
    public String getOverallRatingLabel() {
        double rating = calculateOverallRating();
        return switch ((int) Math.round(rating)) {
            case 5 -> "Outstanding";
            case 4 -> "Exceeds Expectations";
            case 3 -> "Meets Expectations";
            case 2 -> "Needs Improvement";
            case 1 -> "Unsatisfactory";
            default -> "Not Rated";
        };
    }

    // Getters
    public String getEmployeeId() { return employeeId; }
    public String getReviewerId() { return reviewerId; }
    public String getReviewPeriod() { return reviewPeriod; }
    public LocalDate getReviewDate() { return reviewDate; }
    public ReviewStatus getStatus() { return status; }
    public List<ReviewCriteria> getCriteria() { return Collections.unmodifiableList(criteria); }
    public String getOverallRating() { return overallRating; }
    public String getStrengths() { return strengths; }
    public String getAreasForImprovement() { return areasForImprovement; }
    public String getGoals() { return goals; }
    public String getReviewerComments() { return reviewerComments; }
    public String getEmployeeComments() { return employeeComments; }
    public String getNextReviewDate() { return nextReviewDate; }
    public boolean isCompleted() { return completed; }
    public boolean isAcknowledged() { return acknowledged; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }

    public void setOverallRating(String overallRating) {
        this.overallRating = overallRating;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public void setAreasForImprovement(String areasForImprovement) {
        this.areasForImprovement = areasForImprovement;
    }

    public void setGoals(String goals) {
        this.goals = goals;
    }

    public void setReviewerComments(String reviewerComments) {
        this.reviewerComments = reviewerComments;
    }

    public void setEmployeeComments(String employeeComments) {
        this.employeeComments = employeeComments;
    }

    public void setNextReviewDate(String nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    @Override
    public String toString() {
        return "PerformanceReview{" +
                "id=" + id() +
                ", employeeId='" + employeeId + '\'' +
                ", status=" + status +
                ", rating=" + calculateOverallRating() +
                '}';
    }

    /**
     * Review criteria value object.
     */
    public static final class ReviewCriteria implements tech.kayys.syirkah.foundation.domain.valueobject.ValueObject {
        private static final long serialVersionUID = 1L;

        private final String category;
        private final String description;
        private final double rating;
        private final String comments;

        public ReviewCriteria(String category, String description, double rating, String comments) {
            this.category = category;
            this.description = description;
            this.rating = rating;
            this.comments = comments;
            validate();
        }

        public void validate() {
            if (category == null || category.trim().isEmpty()) {
                throw new IllegalArgumentException("Category cannot be empty");
            }
            if (rating < 1 || rating > 5) {
                throw new IllegalArgumentException("Rating must be between 1 and 5");
            }
        }

        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public double getRating() { return rating; }
        public String getComments() { return comments; }

        @Override
        public String toString() {
            return "ReviewCriteria{" +
                    "category='" + category + '\'' +
                    ", rating=" + rating +
                    '}';
        }
    }
}
