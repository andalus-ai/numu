package tech.kayys.syirkah.product.domain.specification;

/**
 * One selectable choice inside an option group (e.g. SMALL, OAT).
 *
 * An option deliberately carries NO price - the commercial effect
 * of selecting it belongs to the pricing capability.
 */
public record OptionDefinition(
        String code,
        String name
) {

    public OptionDefinition {
        code = requireText(code, "Option code");
        name = requireText(name, "Option name");
    }

    /** Identity key for configuration selections (product02.md). */
    public OptionId id() {
        return OptionId.of(code);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return value.trim();
    }
}