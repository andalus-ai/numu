package tech.kayys.numou.product.domain.specification;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttributeValueValidatorTest {

    private final AttributeValueValidator validator = new AttributeValueValidator();

    @Test
    void shouldValidateTextAttribute() {
        AttributeDefinition def = new AttributeDefinition("COLOR", "Color", AttributeType.TEXT, true);

        AttributeValue validVal = new AttributeValue.Text("Blue");
        List<AttributeViolation> violations = validator.validate(def, validVal);
        assertTrue(violations.isEmpty());

        // Null value for required attribute
        violations = validator.validate(def, null);
        assertEquals(1, violations.size());
        assertEquals("REQUIRED", violations.getFirst().violationCode());

        // Type mismatch
        violations = validator.validate(def, new AttributeValue.IntegerNumber(42));
        assertEquals(1, violations.size());
        assertEquals("TYPE_MISMATCH", violations.getFirst().violationCode());
    }

    @Test
    void shouldValidateNumericAttribute() {
        AttributeDefinition def = new AttributeDefinition("WEIGHT", "Weight", AttributeType.NUMBER, false);

        assertTrue(validator.validate(def, new AttributeValue.Decimal(new BigDecimal("12.5"))).isEmpty());
        assertTrue(validator.validate(def, new AttributeValue.IntegerNumber(12)).isEmpty());
        assertTrue(validator.validate(def, null).isEmpty()); // Not required

        List<AttributeViolation> violations = validator.validate(def, new AttributeValue.Text("heavy"));
        assertEquals(1, violations.size());
        assertEquals("TYPE_MISMATCH", violations.getFirst().violationCode());
    }

    @Test
    void shouldValidateBooleanAndDateAttributes() {
        AttributeDefinition boolDef = new AttributeDefinition("IS_ORGANIC", "Is Organic", AttributeType.BOOLEAN, true);
        assertTrue(validator.validate(boolDef, new AttributeValue.Flag(true)).isEmpty());

        AttributeDefinition dateDef = new AttributeDefinition("EXPIRY", "Expiry Date", AttributeType.DATE, true);
        assertTrue(validator.validate(dateDef, new AttributeValue.DateValue(LocalDate.of(2027, 1, 1))).isEmpty());
    }
}
