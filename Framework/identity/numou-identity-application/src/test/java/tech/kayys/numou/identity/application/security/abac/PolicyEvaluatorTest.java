package tech.kayys.syirkah.identity.application.security.abac;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEvaluatorTest {
    private final PolicyEvaluator evaluator = new PolicyEvaluator();
    private final AuthorizationContext context = new AuthorizationContext(
            "tenant-a",
            "purchase-order.approve",
            Map.of("approvalLimit", 50_000L, "warehouseIds", List.of("WH-1")),
            Map.of("amount", 45_000L, "warehouseId", "WH-1", "status", "PENDING"),
            Map.of("channel", "web")
    );

    @Test
    void evaluates_nested_typed_conditions_without_expression_execution() {
        var condition = new AllOf(List.of(
                new Comparison(
                        new AttributeReference(AttributeNamespace.RESOURCE, "amount"),
                        ComparisonOperator.LESS_THAN_OR_EQUAL,
                        50_000L
                ),
                new Contains(
                        new AttributeReference(AttributeNamespace.SUBJECT, "warehouseIds"),
                        "WH-1"
                ),
                new Comparison(
                        new AttributeReference(AttributeNamespace.RESOURCE, "status"),
                        ComparisonOperator.EQUALS,
                        "PENDING"
                )
        ));

        assertTrue(evaluator.evaluate(condition, context));
    }

    @Test
    void unknown_attributes_and_incompatible_comparison_types_fail_closed() {
        var missing = new Comparison(
                new AttributeReference(AttributeNamespace.RESOURCE, "missing"),
                ComparisonOperator.NOT_EQUALS,
                "value"
        );
        var incompatible = new Comparison(
                new AttributeReference(AttributeNamespace.RESOURCE, "amount"),
                ComparisonOperator.GREATER_THAN,
                "large"
        );

        assertFalse(evaluator.evaluate(missing, context));
        assertFalse(evaluator.evaluate(incompatible, context));
    }

    @Test
    void context_defensively_copies_nested_attributes_and_rejects_arbitrary_objects() {
        var values = new java.util.ArrayList<>(List.of("WH-1"));
        var attributes = new java.util.HashMap<String, Object>();
        attributes.put("warehouseIds", values);
        var safe = new AuthorizationContext("tenant-a", "x.y", attributes, Map.of(), Map.of());
        values.add("WH-2");

        assertFalse(evaluator.evaluate(
                new Contains(new AttributeReference(AttributeNamespace.SUBJECT, "warehouseIds"), "WH-2"),
                safe
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizationContext("tenant-a", "x.y", Map.of("obj", new Object()), Map.of(), Map.of())
        );
    }
}
