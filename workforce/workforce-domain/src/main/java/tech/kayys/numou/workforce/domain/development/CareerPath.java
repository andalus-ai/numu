package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.development.event.CareerPathCreated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root representing a structured Career Path within a tenant's organisation.
 *
 * <p>A {@code CareerPath} defines an ordered sequence of positions ({@link CareerPathStep}s)
 * that describe a typical progression route for workers. Paths are tenant-scoped and
 * referenced by {@link WorkerCareerPlan}s to guide individual progression planning.</p>
 *
 * <p>Steps can only be added while the path is {@code ACTIVE}.</p>
 */
public class CareerPath extends AbstractAggregateRoot<CareerPathId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private String description;
    private CareerPathStatus status;
    private final List<CareerPathStep> steps;

    // -------------------------------------------------------------------------
    // Private constructor – use factory method
    // -------------------------------------------------------------------------

    private CareerPath(
            CareerPathId id,
            TenantId tenantId,
            String code,
            String name,
            String description,
            CareerPathStatus status,
            List<CareerPathStep> steps) {
        super(id);
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = status;
        this.steps = new ArrayList<>(steps);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code CareerPath} in {@code ACTIVE} status and registers
     * the {@link CareerPathCreated} domain event.
     *
     * @param id          unique identity.
     * @param tenantId    owning tenant.
     * @param code        unique short code within the tenant (e.g. "ENG-IC").
     * @param name        human-readable name.
     * @param description optional detailed description.
     */
    public static CareerPath create(
            CareerPathId id,
            TenantId tenantId,
            String code,
            String name,
            String description) {

        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");

        CareerPath path = new CareerPath(
                id, tenantId, code, name, description,
                CareerPathStatus.ACTIVE, List.of());

        path.registerEvent(new CareerPathCreated(id, tenantId, code));
        return path;
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Adds a step to this career path.
     * Steps may only be added while the path is {@code ACTIVE}.
     *
     * @param step the career path step to add.
     * @throws IllegalStateException    if the path is not {@code ACTIVE}.
     * @throws IllegalArgumentException if a step with the same {@code stepNumber} already exists.
     */
    public void addStep(CareerPathStep step) {
        if (step == null) throw new IllegalArgumentException("step must not be null");
        if (status != CareerPathStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Steps can only be added to an ACTIVE CareerPath; current status=" + status);
        }
        boolean duplicate = steps.stream()
                .anyMatch(s -> s.stepNumber() == step.stepNumber());
        if (duplicate) {
            throw new IllegalArgumentException(
                    "A step with stepNumber=" + step.stepNumber() + " already exists in this career path");
        }
        steps.add(step);
        // Keep steps ordered by stepNumber for deterministic reads.
        steps.sort(java.util.Comparator.comparingInt(CareerPathStep::stepNumber));
    }

    /**
     * Activates an {@code INACTIVE} career path.
     *
     * @throws IllegalStateException if the path is already {@code ACTIVE}.
     */
    public void activate() {
        if (status == CareerPathStatus.ACTIVE) {
            throw new IllegalStateException("CareerPath is already ACTIVE");
        }
        this.status = CareerPathStatus.ACTIVE;
    }

    /**
     * Deactivates an {@code ACTIVE} career path, preventing new assignments.
     *
     * @throws IllegalStateException if the path is already {@code INACTIVE}.
     */
    public void deactivate() {
        if (status == CareerPathStatus.INACTIVE) {
            throw new IllegalStateException("CareerPath is already INACTIVE");
        }
        this.status = CareerPathStatus.INACTIVE;
    }

    /** Updates the human-readable name. */
    public void updateName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = name;
    }

    /** Updates the description. */
    public void updateDescription(String description) {
        this.description = description;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public TenantId getTenantId() { return tenantId; }

    public String getCode() { return code; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public CareerPathStatus getStatus() { return status; }

    /** Returns an unmodifiable, ordered view of the career path steps. */
    public List<CareerPathStep> getSteps() { return Collections.unmodifiableList(steps); }
}
