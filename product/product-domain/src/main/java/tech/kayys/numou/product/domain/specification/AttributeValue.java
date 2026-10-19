package tech.kayys.numou.product.domain.specification;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Governed typed attribute value representation (config03.md §P4-18 #5).
 * Eliminates unvalidated string-only attribute values while preserving type safety.
 */
public sealed interface AttributeValue extends Serializable
        permits AttributeValue.Text,
                AttributeValue.Decimal,
                AttributeValue.IntegerNumber,
                AttributeValue.Flag,
                AttributeValue.DateValue,
                AttributeValue.Enumeration,
                AttributeValue.TextList {

    record Text(String value) implements AttributeValue {
        public Text {
            Objects.requireNonNull(value, "value cannot be null");
        }
    }

    record Decimal(BigDecimal value) implements AttributeValue {
        public Decimal {
            Objects.requireNonNull(value, "value cannot be null");
        }
    }

    record IntegerNumber(long value) implements AttributeValue {}

    record Flag(boolean value) implements AttributeValue {}

    record DateValue(LocalDate value) implements AttributeValue {
        public DateValue {
            Objects.requireNonNull(value, "value cannot be null");
        }
    }

    record Enumeration(String code) implements AttributeValue {
        public Enumeration {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("Enumeration code cannot be blank");
            }
            code = code.trim();
        }
    }

    record TextList(List<String> values) implements AttributeValue {
        public TextList {
            values = List.copyOf(Objects.requireNonNull(values, "values cannot be null"));
            if (values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Attribute values cannot contain null");
            }
        }
    }
}
