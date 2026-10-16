package tech.kayys.syirkah.product.domain.specification;

import java.util.List;
import java.util.Objects;

/**
 * A named group of selectable options (product02.md).
 *
 * Empty groups are allowed at creation — options are added later.
 * {@code multiSelect} controls whether a configuration may pick more
 * than one option from this group.
 */
public record OptionGroup(
        String code,
        String name,
        boolean required,
        boolean multiSelect,
        List<OptionDefinition> options
) {

    public OptionGroup {
        Objects.requireNonNull(options, "options cannot be null");

        code = requireText(code, "Option group code");
        name = requireText(name, "Option group name");

        if (!multiSelect && options.size() > 1) {
            // single-select groups may still list many options;
            // multiSelect only constrains configuration selection count
        }

        options = List.copyOf(options);
    }

    /** Backward-compatible ctor: single-select, required options list. */
    public OptionGroup(
            String code,
            String name,
            boolean required,
            List<OptionDefinition> options
    ) {
        this(code, name, required, false, options);
    }

    public static OptionGroup create(
            String code,
            String name,
            boolean required,
            boolean multiSelect
    ) {
        return new OptionGroup(code, name, required, multiSelect, List.of());
    }

    /** Returns a copy with {@code option} appended (rejecting duplicates). */
    public OptionGroup withOption(OptionDefinition option) {
        Objects.requireNonNull(option, "option cannot be null");
        boolean exists = options.stream()
                .anyMatch(existing -> existing.code().equals(option.code()));
        if (exists) {
            throw new IllegalArgumentException(
                    "Option already exists: " + option.code()
            );
        }
        var next = new java.util.ArrayList<>(options);
        next.add(option);
        return new OptionGroup(code, name, required, multiSelect, next);
    }

    /**
     * Returns a copy without the option identified by {@code optionCode}.
     */
    public OptionGroup withoutOption(String optionCode) {
        String normalized = requireText(optionCode, "Option code");
        var next = options.stream()
                .filter(option -> !option.code().equals(normalized))
                .toList();
        if (next.size() == options.size()) {
            throw new IllegalArgumentException(
                    "Option not found: " + normalized
            );
        }
        return new OptionGroup(code, name, required, multiSelect, next);
    }

    /** Identity key for configuration selections (product02.md). */
    public OptionGroupId id() {
        return OptionGroupId.of(code);
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
