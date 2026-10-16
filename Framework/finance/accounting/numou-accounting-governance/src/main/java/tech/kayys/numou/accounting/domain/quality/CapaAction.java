package tech.kayys.syirkah.accounting.domain.quality;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CapaAction {
    public enum Status {
        OPEN, IN_PROGRESS, RESOLVED, VERIFIED, VERIFICATION, ROOT_CAUSE_ANALYSIS, ACTION_PLANNING, IMPLEMENTATION, CLOSED
    }

    private final String id;
    private String rootCause;
    private final String actionPlan;
    private final String owner;
    private final LocalDate targetDate;
    private Status status;
    private final List<String> fiveWhys = new ArrayList<>();
    private final List<String> correctiveActions = new ArrayList<>();
    private final List<String> preventiveActions = new ArrayList<>();
    private String verificationNotes;

    public CapaAction(String id, String rootCause, String actionPlan, String owner, LocalDate targetDate) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.rootCause = Objects.requireNonNull(rootCause, "rootCause must not be null");
        this.actionPlan = Objects.requireNonNull(actionPlan, "actionPlan must not be null");
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.targetDate = Objects.requireNonNull(targetDate, "targetDate must not be null");
        this.status = Status.OPEN;
    }

    public void startProgress() {
        this.status = Status.IN_PROGRESS;
    }

    public void resolve() {
        this.status = Status.RESOLVED;
    }

    public void verify() {
        this.status = Status.VERIFIED;
    }

    public void recordFiveWhys(List<String> whys, String concludedRootCause) {
        this.fiveWhys.clear();
        this.fiveWhys.addAll(Objects.requireNonNull(whys, "whys must not be null"));
        if (concludedRootCause != null && !concludedRootCause.isBlank()) {
            this.rootCause = concludedRootCause;
        }
        this.status = Status.ROOT_CAUSE_ANALYSIS;
    }

    public void addCorrectiveAction(String action) {
        correctiveActions.add(Objects.requireNonNull(action, "action must not be null"));
        this.status = Status.ACTION_PLANNING;
    }

    public void addPreventiveAction(String action) {
        preventiveActions.add(Objects.requireNonNull(action, "action must not be null"));
        this.status = Status.ACTION_PLANNING;
    }

    public void advanceToImplementation() {
        this.status = Status.IMPLEMENTATION;
    }

    public void advanceToVerification() {
        this.status = Status.VERIFICATION;
    }

    public void close(String verificationNotes) {
        this.verificationNotes = verificationNotes;
        this.status = Status.CLOSED;
    }

    public String id() { return id; }
    public String capaId() { return id; }
    public String rootCause() { return rootCause; }
    public String actionPlan() { return actionPlan; }
    public String actionDescription() { return actionPlan; }
    public String owner() { return owner; }
    public String ownerId() { return owner; }
    public LocalDate targetDate() { return targetDate; }
    public Status status() { return status; }
    public List<String> fiveWhys() { return List.copyOf(fiveWhys); }
    public List<String> correctiveActions() { return List.copyOf(correctiveActions); }
    public List<String> preventiveActions() { return List.copyOf(preventiveActions); }
    public String verificationNotes() { return verificationNotes; }
}
