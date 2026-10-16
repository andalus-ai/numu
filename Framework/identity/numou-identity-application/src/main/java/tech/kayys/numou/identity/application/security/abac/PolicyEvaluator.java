package tech.kayys.syirkah.identity.application.security.abac;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

public final class PolicyEvaluator {
    public boolean evaluate(PolicyCondition condition, AuthorizationContext context) {
        return evaluateTruth(condition, context).orElse(false);
    }

    private Optional<Boolean> evaluateTruth(PolicyCondition condition, AuthorizationContext context) {
        return switch (condition) {
            case AllOf all -> allOf(all, context);
            case AnyOf any -> anyOf(any, context);
            case Not not -> evaluateTruth(not.condition(), context).map(value -> !value);
            case Comparison comparison -> compare(
                    context.value(comparison.left()), comparison.operator(), comparison.right()
            );
            case Contains contains -> contains(context.value(contains.collection()), contains.value());
        };
    }

    private Optional<Boolean> allOf(AllOf all, AuthorizationContext context) {
        var unknown = false;
        for (var condition : all.conditions()) {
            var value = evaluateTruth(condition, context);
            if (value.isPresent() && !value.get()) {
                return Optional.of(false);
            }
            unknown |= value.isEmpty();
        }
        return unknown ? Optional.empty() : Optional.of(true);
    }

    private Optional<Boolean> anyOf(AnyOf any, AuthorizationContext context) {
        var unknown = false;
        for (var condition : any.conditions()) {
            var value = evaluateTruth(condition, context);
            if (value.isPresent() && value.get()) {
                return Optional.of(true);
            }
            unknown |= value.isEmpty();
        }
        return unknown ? Optional.empty() : Optional.of(false);
    }

    private static Optional<Boolean> compare(Object left, ComparisonOperator operator, Object right) {
        if (left == null) {
            return Optional.empty();
        }
        if (operator == ComparisonOperator.EQUALS || operator == ComparisonOperator.NOT_EQUALS) {
            if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                var equal = new BigDecimal(leftNumber.toString()).compareTo(
                        new BigDecimal(rightNumber.toString())
                ) == 0;
                return Optional.of(operator == ComparisonOperator.EQUALS ? equal : !equal);
            }
            if (!left.getClass().equals(right.getClass())
                    || !(left instanceof String || left instanceof Boolean || left instanceof java.util.UUID
                    || left instanceof java.time.Instant || left instanceof java.time.LocalDate)) {
                return Optional.of(false);
            }
            var equal = left.equals(right);
            return Optional.of(operator == ComparisonOperator.EQUALS ? equal : !equal);
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Optional.of(compareOrder(new BigDecimal(leftNumber.toString()).compareTo(
                    new BigDecimal(rightNumber.toString())), operator));
        }
        if (left instanceof String leftString && right instanceof String rightString) {
            return Optional.of(compareOrder(leftString.compareTo(rightString), operator));
        }
        return Optional.of(false);
    }

    private static boolean compareOrder(int comparison, ComparisonOperator operator) {
        return switch (operator) {
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            case EQUALS, NOT_EQUALS -> false;
        };
    }

    private static Optional<Boolean> contains(Object collection, Object value) {
        if (collection == null) {
            return Optional.empty();
        }
        if (collection instanceof Collection<?> values) {
            return Optional.of(values.contains(value));
        }
        if (collection instanceof String text && value instanceof String fragment) {
            return Optional.of(text.contains(fragment));
        }
        return Optional.of(false);
    }
}
