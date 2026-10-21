package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Editable schedule with append-only baselines. A baseline captures the plan at
 * a point in time; changing the working plan never rewrites an earlier snapshot.
 */
public final class SchedulePlan {
    private final PlanId id;
    private final String code;
    private String name;
    private PlanStatus status = PlanStatus.DRAFT;
    private final List<ScheduleActivity> activities = new ArrayList<>();
    private final List<ActivityDependency> dependencies = new ArrayList<>();
    private final List<ScheduleBaseline> baselines = new ArrayList<>();

    private SchedulePlan(PlanId id, String code, String name) {
        this.id = Objects.requireNonNull(id, "Plan id cannot be null");
        this.code = requireText(code, "Plan code");
        this.name = requireText(name, "Plan name");
    }

    public static SchedulePlan create(String code, String name) {
        return new SchedulePlan(PlanId.generate(), code, name);
    }

    public void rename(String name) {
        ensureDraft();
        this.name = requireText(name, "Plan name");
    }

    public void activate() {
        if (status != PlanStatus.DRAFT) throw new IllegalStateException("Only a draft plan can be activated");
        if (activities.isEmpty()) throw new IllegalStateException("A plan needs at least one activity");
        status = PlanStatus.ACTIVE;
    }

    public void retire() {
        if (status != PlanStatus.ACTIVE) throw new IllegalStateException("Only an active plan can be retired");
        status = PlanStatus.RETIRED;
    }

    public void addActivity(ScheduleActivity activity) {
        ensureEditable();
        Objects.requireNonNull(activity, "Activity cannot be null");
        if (activities.stream().anyMatch(existing -> existing.id().equals(activity.id()))) {
            throw new IllegalArgumentException("Activity id already exists in this plan");
        }
        if (activities.stream().anyMatch(existing -> existing.code().equalsIgnoreCase(activity.code()))) {
            throw new IllegalArgumentException("Activity code must be unique within a plan");
        }
        activities.add(activity);
    }

    public void replaceActivity(ScheduleActivity replacement) {
        ensureEditable();
        Objects.requireNonNull(replacement, "Activity cannot be null");
        int index = indexOf(replacement.id());
        if (activities.stream().anyMatch(existing -> !existing.id().equals(replacement.id())
                && existing.code().equalsIgnoreCase(replacement.code()))) {
            throw new IllegalArgumentException("Activity code must be unique within a plan");
        }
        activities.set(index, replacement);
    }

    public void addDependency(ActivityDependency dependency) {
        ensureEditable();
        Objects.requireNonNull(dependency, "Dependency cannot be null");
        if (!containsActivity(dependency.predecessor()) || !containsActivity(dependency.successor())) {
            throw new IllegalArgumentException("Both dependency endpoints must belong to this plan");
        }
        if (dependencies.contains(dependency)) throw new IllegalArgumentException("Dependency already exists");
        dependencies.add(dependency);
        if (hasDependencyCycle()) {
            dependencies.remove(dependencies.size() - 1);
            throw new IllegalArgumentException("Activity dependencies cannot contain a cycle");
        }
    }

    public ScheduleBaseline createBaseline(String name, Instant createdAt, String createdBy) {
        if (status == PlanStatus.RETIRED) throw new IllegalStateException("Retired plans cannot be baselined");
        if (activities.isEmpty()) throw new IllegalStateException("Cannot baseline an empty plan");
        validateDependencies();
        ScheduleBaseline baseline = ScheduleBaseline.snapshot(
                baselines.size() + 1, name, createdAt, createdBy, activities, dependencies);
        baselines.add(baseline);
        return baseline;
    }

    private boolean hasDependencyCycle() {
        var outgoing = new java.util.HashMap<ActivityId, List<ActivityId>>();
        for (ActivityDependency edge : dependencies) {
            outgoing.computeIfAbsent(edge.predecessor(), ignored -> new ArrayList<>()).add(edge.successor());
        }
        Set<ActivityId> complete = new HashSet<>();
        Set<ActivityId> active = new HashSet<>();
        for (ScheduleActivity activity : activities) {
            if (cycleFrom(activity.id(), outgoing, complete, active)) return true;
        }
        return false;
    }

    private boolean cycleFrom(ActivityId id, java.util.Map<ActivityId, List<ActivityId>> outgoing,
                              Set<ActivityId> complete, Set<ActivityId> active) {
        if (active.contains(id)) return true;
        if (complete.contains(id)) return false;
        active.add(id);
        for (ActivityId next : outgoing.getOrDefault(id, List.of())) {
            if (cycleFrom(next, outgoing, complete, active)) return true;
        }
        active.remove(id);
        complete.add(id);
        return false;
    }

    private void validateDependencies() {
        Set<ActivityId> ids = new HashSet<>();
        activities.forEach(activity -> ids.add(activity.id()));
        if (dependencies.stream().anyMatch(d -> !ids.contains(d.predecessor()) || !ids.contains(d.successor()))) {
            throw new IllegalStateException("Plan contains a dependency to a missing activity");
        }
        if (hasDependencyCycle()) throw new IllegalStateException("Plan contains cyclic dependencies");
    }

    private boolean containsActivity(ActivityId id) {
        return activities.stream().anyMatch(activity -> activity.id().equals(id));
    }

    private int indexOf(ActivityId id) {
        for (int i = 0; i < activities.size(); i++) {
            if (activities.get(i).id().equals(id)) return i;
        }
        throw new IllegalArgumentException("Activity does not belong to this plan");
    }

    private void ensureEditable() {
        if (status == PlanStatus.RETIRED) throw new IllegalStateException("Retired plans cannot be edited");
    }

    private void ensureDraft() {
        if (status != PlanStatus.DRAFT) throw new IllegalStateException("Only draft plans can be renamed");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }

    public PlanId id() { return id; }
    public String code() { return code; }
    public String name() { return name; }
    public PlanStatus status() { return status; }
    public List<ScheduleActivity> activities() { return List.copyOf(activities); }
    public List<ActivityDependency> dependencies() { return List.copyOf(dependencies); }
    public List<ScheduleBaseline> baselines() { return List.copyOf(baselines); }
}
