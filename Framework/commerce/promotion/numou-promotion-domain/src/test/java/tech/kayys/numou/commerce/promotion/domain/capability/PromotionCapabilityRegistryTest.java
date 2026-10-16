package tech.kayys.syirkah.commerce.promotion.domain.capability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.EffectType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.PromotionCompilationException;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.TargetType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Versioned capability registry (product03.md §61-§70).
 *
 * <p>The two properties that matter: lookup is by {@code type + version} so a
 * compilation is deterministic, and every registration carries its descriptor so
 * the capability is self-describing for the tenant-facing API (§71).</p>
 */
@DisplayName("Promotion capability registry")
class PromotionCapabilityRegistryTest {

    private final PromotionCapabilityRegistry registry = PromotionCapabilities.defaults();

    @Test
    void registersEveryShippedCapability() {
        assertEquals(2, registry.conditions().size());
        assertEquals(5, registry.targets().size());
        assertEquals(2, registry.effects().size());
    }

    @Test
    void lookupIsVersioned() {
        var condition = registry.requireCondition(ConditionType.of("channel"), "1");

        assertEquals("channel", condition.type().value());
        assertEquals("1", condition.version());
        assertNotNull(condition.descriptor());
        assertNotNull(condition.compile(tech.kayys.syirkah.commerce.promotion.domain.compiler
                .ConditionDefinition.leaf(
                        ConditionType.of("channel"),
                        tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionOperator.EQUALS,
                        "POS")));

        assertThrows(PromotionCompilationException.class,
                () -> registry.requireCondition(ConditionType.of("channel"), "2"));
    }

    @Test
    void unknownEffectIsRejected() {
        assertThrows(PromotionCompilationException.class,
                () -> registry.requireEffect(EffectType.of("no_such_effect"), "1"));
    }

    @Test
    void unknownTargetIsRejected() {
        assertThrows(PromotionCompilationException.class,
                () -> registry.requireTarget(TargetType.of("no_such_target"), "1"));
    }

    @Test
    void descriptorsDeclareOperatorsAndSchemas() {
        var channel = registry.requireCondition(ConditionType.of("channel"), "1");
        var descriptor = channel.descriptor();

        assertEquals(PromotionCapabilityKind.CONDITION, descriptor.kind());
        assertTrue(descriptor.supportedOperators().contains(
                tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionOperator.EQUALS));
        assertEquals(PromotionValueType.STRING, descriptor.valueType());
        assertTrue(descriptor.schema().isEmpty());

        var percentage = registry.requireEffect(EffectType.of("percentage_discount"), "1");
        assertEquals(PromotionCapabilityKind.EFFECT, percentage.descriptor().kind());
        assertFalse(percentage.descriptor().parameterSchema().isEmpty());
        assertEquals(1, percentage.descriptor().parameterSchema().parameters().size());
        assertEquals("percentage",
                percentage.descriptor().parameterSchema().parameters().getFirst().name());
    }

    @Test
    void capabilityIdFormatsTypeAtVersion() {
        assertEquals("percentage_discount@1",
                new PromotionCapabilityId("percentage_discount", "1").toString());
    }
}