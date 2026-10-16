package tech.kayys.syirkah.hris.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.hris.domain.identifier.OnboardingId;
import tech.kayys.syirkah.hris.domain.valueobject.OnboardingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Onboarding process aggregate root.
 * Manages the onboarding of new employees.
 */
public final class OnboardingProcess extends AbstractAggregateRoot<OnboardingId> {

    private static final long serialVersionUID = 1L;

    private String employeeId;
    private String employeeName;
    private LocalDate startDate;
    private LocalDate expectedCompletionDate;
    private LocalDate actualCompletionDate;
    private OnboardingStatus status;
    private List<OnboardingTask> tasks;
    private String assignedTo;
    private String department;
    private String position;
    private String mentorId;
    private String paperworkStatus;
    private boolean equipmentIssued;
    private boolean trainingCompleted;
    private boolean orientationCompleted;
    private String notes;
    private boolean completed;

    private OnboardingProcess(OnboardingId id) {
        super();
        this.tasks = new ArrayList<>();
        this.status = OnboardingStatus.INITIATED;
        this.completed = false;
    }

    /**
     * Factory method to create a new onboarding process.
     */
    public static OnboardingProcess create(
            OnboardingId id,
            String employeeId,
            String employeeName,
            LocalDate startDate,
            String department,
            String position) {
        OnboardingProcess process = new OnboardingProcess(id);
        process.employeeId = employeeId;
        process.employeeName = employeeName;
        process.startDate = startDate;
        process.department = department;
        process.position = position;
        process.expectedCompletionDate = startDate.plusDays(30);
        return process;
    }

    @Override
    public OnboardingId id() {
        return null;
    }

    /**
     * Adds a task to the onboarding process.
     */
    public void addTask(OnboardingTask task) {
        if (status == OnboardingStatus.COMPLETED || status == OnboardingStatus.FAILED) {
            throw new IllegalStateException("Cannot modify completed onboarding");
        }
        tasks.add(task);
        updateStatus();
    }

    /**
     * Completes a task in the onboarding process.
     */
    public void completeTask(String taskId, String completedBy) {
        if (status == OnboardingStatus.COMPLETED || status == OnboardingStatus.FAILED) {
            throw new IllegalStateException("Cannot modify completed onboarding");
        }
        OnboardingTask task = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        task.complete(completedBy);
        updateStatus();
    }

    /**
     * Updates the overall status based on tasks.
     */
    private void updateStatus() {
        boolean allCompleted = tasks.stream().allMatch(OnboardingTask::isCompleted);
        boolean anyFailed = tasks.stream().anyMatch(t -> !t.isCompleted() && t.isRequired());

        if (allCompleted) {
            this.status = OnboardingStatus.COMPLETED;
            this.completed = true;
            this.actualCompletionDate = LocalDate.now();
        } else if (anyFailed) {
            this.status = OnboardingStatus.FAILED;
        } else {
            this.status = getStatusFromTasks();
        }
    }

    private OnboardingStatus getStatusFromTasks() {
        int completedCount = (int) tasks.stream().filter(OnboardingTask::isCompleted).count();
        double progress = (double) completedCount / tasks.size() * 100.0;

        if (progress < 25) return OnboardingStatus.INITIATED;
        if (progress < 50) return OnboardingStatus.DOCUMENTATION;
        if (progress < 75) return OnboardingStatus.ORIENTATION;
        return OnboardingStatus.TRAINING;
    }

    /**
     * Gets the completion progress.
     */
    public double getProgress() {
        if (tasks.isEmpty()) {
            return 0.0;
        }
        long completed = tasks.stream().filter(OnboardingTask::isCompleted).count();
        return (double) completed / tasks.size() * 100.0;
    }

    /**
     * Marks the onboarding as completed.
     */
    public void complete() {
        if (status == OnboardingStatus.COMPLETED) {
            return;
        }
        if (!tasks.stream().allMatch(OnboardingTask::isCompleted)) {
            throw new IllegalStateException("All tasks must be completed");
        }
        this.status = OnboardingStatus.COMPLETED;
        this.completed = true;
        this.actualCompletionDate = LocalDate.now();
    }

    // Getters
    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getExpectedCompletionDate() { return expectedCompletionDate; }
    public LocalDate getActualCompletionDate() { return actualCompletionDate; }
    public OnboardingStatus getStatus() { return status; }
    public List<OnboardingTask> getTasks() { return Collections.unmodifiableList(tasks); }
    public String getAssignedTo() { return assignedTo; }
    public String getDepartment() { return department; }
    public String getPosition() { return position; }
    public String getMentorId() { return mentorId; }
    public String getPaperworkStatus() { return paperworkStatus; }
    public boolean isEquipmentIssued() { return equipmentIssued; }
    public boolean isTrainingCompleted() { return trainingCompleted; }
    public boolean isOrientationCompleted() { return orientationCompleted; }
    public String getNotes() { return notes; }
    public boolean isCompleted() { return completed; }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public void setMentorId(String mentorId) {
        this.mentorId = mentorId;
    }

    public void setPaperworkStatus(String paperworkStatus) {
        this.paperworkStatus = paperworkStatus;
    }

    public void setEquipmentIssued(boolean equipmentIssued) {
        this.equipmentIssued = equipmentIssued;
    }

    public void setTrainingCompleted(boolean trainingCompleted) {
        this.trainingCompleted = trainingCompleted;
    }

    public void setOrientationCompleted(boolean orientationCompleted) {
        this.orientationCompleted = orientationCompleted;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "OnboardingProcess{" +
                "id=" + id() +
                ", employeeName='" + employeeName + '\'' +
                ", status=" + status +
                ", progress=" + getProgress() + "%" +
                '}';
    }

    /**
     * Onboarding task value object.
     */
    public static final class OnboardingTask implements tech.kayys.syirkah.foundation.domain.valueobject.ValueObject {
        private static final long serialVersionUID = 1L;

        private final String id;
        private final String name;
        private final String description;
        private final boolean required;
        private boolean completed;
        private String completedBy;
        private Instant completedAt;

        public OnboardingTask(String id, String name, String description, boolean required) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.required = required;
            this.completed = false;
            validate();
        }

        public void validate() {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Task ID cannot be empty");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Task name cannot be empty");
            }
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
        public boolean isCompleted() { return completed; }
        public String getCompletedBy() { return completedBy; }
        public Instant getCompletedAt() { return completedAt; }

        public void complete(String completedBy) {
            this.completed = true;
            this.completedBy = completedBy;
            this.completedAt = Instant.now();
        }

        @Override
        public String toString() {
            return "OnboardingTask{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", completed=" + completed +
                    '}';
        }
    }
}
