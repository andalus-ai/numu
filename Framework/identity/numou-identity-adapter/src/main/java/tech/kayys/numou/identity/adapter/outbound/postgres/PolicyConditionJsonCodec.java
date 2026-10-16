package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.identity.application.security.abac.AllOf;
import tech.kayys.syirkah.identity.application.security.abac.AnyOf;
import tech.kayys.syirkah.identity.application.security.abac.AttributeNamespace;
import tech.kayys.syirkah.identity.application.security.abac.AttributeReference;
import tech.kayys.syirkah.identity.application.security.abac.Comparison;
import tech.kayys.syirkah.identity.application.security.abac.ComparisonOperator;
import tech.kayys.syirkah.identity.application.security.abac.Contains;
import tech.kayys.syirkah.identity.application.security.abac.Not;
import tech.kayys.syirkah.identity.application.security.abac.PolicyCondition;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PolicyConditionJsonCodec {
    private final ObjectMapper objectMapper;

    @Inject
    public PolicyConditionJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PolicyCondition decode(String json) {
        try {
            return decode(objectMapper.readTree(json));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored authorization policy condition is invalid JSON", exception);
        }
    }

    private PolicyCondition decode(JsonNode node) {
        requireObject(node);
        var type = requiredText(node, "type");
        return switch (type) {
            case "all" -> new AllOf(decodeChildren(node));
            case "any" -> new AnyOf(decodeChildren(node));
            case "not" -> new Not(decode(required(node, "condition")));
            case "comparison" -> new Comparison(
                    attribute(required(node, "attribute")),
                    enumValue(ComparisonOperator.class, requiredText(node, "operator")),
                    literal(required(node, "value"))
            );
            case "contains" -> new Contains(
                    attribute(required(node, "collection")),
                    literal(required(node, "value"))
            );
            default -> throw new IllegalArgumentException("Unsupported policy condition type: " + type);
        };
    }

    private List<PolicyCondition> decodeChildren(JsonNode node) {
        var children = required(node, "conditions");
        if (!children.isArray() || children.isEmpty()) {
            throw new IllegalArgumentException("Policy conditions must be a non-empty array");
        }
        var decoded = new ArrayList<PolicyCondition>(children.size());
        children.forEach(child -> decoded.add(decode(child)));
        return List.copyOf(decoded);
    }

    private static AttributeReference attribute(JsonNode node) {
        requireObject(node);
        return new AttributeReference(
                enumValue(AttributeNamespace.class, requiredText(node, "namespace")),
                requiredText(node, "name")
        );
    }

    private static Object literal(JsonNode node) {
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        throw new IllegalArgumentException("Policy literal must be a string, number, or boolean");
    }

    private static JsonNode required(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing policy condition field: " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        var value = required(node, field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Policy condition field must be non-blank text: " + field);
        }
        return value.textValue();
    }

    private static void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Policy condition must be a JSON object");
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException invalidValue) {
            throw new IllegalArgumentException("Invalid " + enumType.getSimpleName() + ": " + value, invalidValue);
        }
    }
}
