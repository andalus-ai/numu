package tech.kayys.syirkah.commerce.configuration.domain;

import tech.kayys.syirkah.product.domain.specification.OptionGroup;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Validates configuration selections against a specification (product02.md).
 *
 * Rules: product match, known group/option, required groups, single-select
 * cardinality, multi-select allowed.
 */
public final class ConfigurationValidator {

    private ConfigurationValidator() {
    }

    public static ConfigurationValidationResult validate(
            ProductSpecification specification,
            ProductConfiguration configuration
    ) {
        Objects.requireNonNull(specification, "specification cannot be null");
        Objects.requireNonNull(configuration, "configuration cannot be null");

        List<ConfigurationValidationError> errors = new ArrayList<>();

        if (!specification.productId().equals(configuration.productId())) {
            errors.add(ConfigurationValidationError.of(
                    "PRODUCT_MISMATCH",
                    "Configuration is for product "
                            + configuration.productId().value()
                            + " but specification belongs to product "
                            + specification.productId().value()));
            return ConfigurationValidationResult.invalid(errors);
        }

        if (configuration.specificationId() != null
                && !configuration.specificationId().equals(specification.id())) {
            errors.add(ConfigurationValidationError.of(
                    "SPECIFICATION_MISMATCH",
                    "Configuration specification does not match"));
        }

        var groupsByCode = new HashMap<String, OptionGroup>();
        for (var group : specification.optionGroups()) {
            groupsByCode.put(group.code(), group);
        }

        for (var group : specification.optionGroups()) {
            long count = configuration.selections().stream()
                    .filter(s -> s.groupCode().equals(group.code()))
                    .count();
            if (count == 0 && group.required()) {
                errors.add(ConfigurationValidationError.of(
                        "REQUIRED_GROUP",
                        "Missing required selection for group: " + group.code()));
            }
            if (!group.multiSelect() && count > 1) {
                errors.add(ConfigurationValidationError.of(
                        "SINGLE_SELECT",
                        "Group '" + group.code()
                                + "' allows only one selection"));
            }
        }

        for (var selection : configuration.selections()) {
            var group = groupsByCode.get(selection.groupCode());
            if (group == null) {
                errors.add(ConfigurationValidationError.of(
                        "UNKNOWN_GROUP",
                        "Unknown option group: " + selection.groupCode()));
                continue;
            }
            boolean known = group.options().stream()
                    .anyMatch(option -> option.code().equals(selection.optionCode()));
            if (!known) {
                errors.add(ConfigurationValidationError.of(
                        "UNKNOWN_OPTION",
                        "Unknown option '" + selection.optionCode()
                                + "' in group '" + selection.groupCode() + "'"));
            }
        }

        if (errors.isEmpty()) {
            return ConfigurationValidationResult.valid();
        }
        return ConfigurationValidationResult.invalid(errors);
    }
}
