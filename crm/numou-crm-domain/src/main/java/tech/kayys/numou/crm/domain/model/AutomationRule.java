package tech.kayys.numou.crm.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.identifier.AutomationRuleId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Automation rule aggregate root.
 * Defines rules for automated CRM actions.
 */
public final class AutomationRule extends AbstractAggregateRoot<AutomationRuleId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private String triggerEvent; // LEAD_CREATED, TICKET_CREATED, OPPORTUNITY_STAGE_CHANGED, etc.
    private List<Condition> conditions;
    private List<Action> actions;
    private String priority;
    private boolean active;
    private String createdBy;
    private String notes;

    private AutomationRule(AutomationRuleId id) {
        super(id);
        this.conditions = new ArrayList<>();
        this.actions = new ArrayList<>();
        this.active = true;
    }

    private AutomationRule() {
        super();
    }

    /**
     * Factory method to create a new automation rule.
     */
    public static AutomationRule create(
            AutomationRuleId id,
            String name,
            String triggerEvent,
            String createdBy) {
        AutomationRule rule = new AutomationRule(id);
        rule.name = name;
        rule.triggerEvent = triggerEvent;
        rule.createdBy = createdBy;
        return rule;
    }

    /**
     * Adds a condition to the rule.
     */
    public void addCondition(Condition condition) {
        conditions.add(condition);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds an action to the rule.
     */
    public void addAction(Action action) {
        actions.add(action);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Activates the rule.
     */
    public void activate() {
        this.active = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Deactivates the rule.
     */
    public void deactivate() {
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Evaluates if the rule matches the given context.
     */
    public boolean matches(Map<String, Object> context) {
        if (!active) {
            return false;
        }
        return conditions.stream().allMatch(c -> c.evaluate(context));
    }

    /**
     * Executes the rule's actions.
     */
    public void execute(Map<String, Object> context) {
        for (Action action : actions) {
            action.execute(context);
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getTriggerEvent() { return triggerEvent; }
    public List<Condition> getConditions() { return Collections.unmodifiableList(conditions); }
    public List<Action> getActions() { return Collections.unmodifiableList(actions); }
    public String getPriority() { return priority; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public String getNotes() { return notes; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPriority(String priority) {
        this.priority = priority;
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
        return "AutomationRule{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", triggerEvent='" + triggerEvent + '\'' +
                ", conditions=" + conditions.size() +
                ", actions=" + actions.size() +
                ", active=" + active +
                '}';
    }

    /**
     * Condition value object.
     */
    public static final class Condition implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String field;
        private final String operator; // EQ, NEQ, GT, LT, CONTAINS, STARTS_WITH, ENDS_WITH, IN
        private final String value;

        public Condition(String field, String operator, String value) {
            this.field = field;
            this.operator = operator;
            this.value = value;
            validate();
        }

        @Override
        public void validate() {
            if (field == null || field.trim().isEmpty()) {
                throw new IllegalArgumentException("Field cannot be empty");
            }
            if (operator == null || operator.trim().isEmpty()) {
                throw new IllegalArgumentException("Operator cannot be empty");
            }
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("Value cannot be empty");
            }
        }

        public String getField() { return field; }
        public String getOperator() { return operator; }
        public String getValue() { return value; }

        public boolean evaluate(Map<String, Object> context) {
            Object fieldValue = context.get(field);
            if (fieldValue == null) {
                return false;
            }
            
            String strValue = fieldValue.toString();
            return switch (operator) {
                case "EQ" -> strValue.equals(value);
                case "NEQ" -> !strValue.equals(value);
                case "CONTAINS" -> strValue.contains(value);
                case "STARTS_WITH" -> strValue.startsWith(value);
                case "ENDS_WITH" -> strValue.endsWith(value);
                default -> false;
            };
        }

        @Override
        public String toString() {
            return "Condition{" +
                    "field='" + field + '\'' +
                    ", operator='" + operator + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }

    /**
     * Action value object.
     */
    public static final class Action implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String type; // ASSIGN, SEND_EMAIL, UPDATE_FIELD, CREATE_TASK, NOTIFY, ESCALATE
        private final Map<String, String> parameters;

        public Action(String type, Map<String, String> parameters) {
            this.type = type;
            this.parameters = parameters;
            validate();
        }

        @Override
        public void validate() {
            if (type == null || type.trim().isEmpty()) {
                throw new IllegalArgumentException("Action type cannot be empty");
            }
        }

        public String getType() { return type; }
        public Map<String, String> getParameters() { return Collections.unmodifiableMap(parameters); }

        public void execute(Map<String, Object> context) {
            // Implementation will be handled by the action executor service
        }

        @Override
        public String toString() {
            return "Action{" +
                    "type='" + type + '\'' +
                    ", parameters=" + parameters +
                    '}';
        }
    }
}