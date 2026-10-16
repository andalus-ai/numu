package tech.kayys.syirkah.commerce.configuration.domain;

import tech.kayys.syirkah.product.domain.specification.OptionGroupId;
import tech.kayys.syirkah.product.domain.specification.OptionId;

import java.util.Objects;

/**
 * One customer choice: IDs only, not specification objects (product02.md).
 */
public record SelectedOption(
        OptionGroupId optionGroupId,
        OptionId optionId
) {

    public SelectedOption {
        Objects.requireNonNull(optionGroupId, "optionGroupId cannot be null");
        Objects.requireNonNull(optionId, "optionId cannot be null");
    }

    /** Convenience for code-based call sites / pricing. */
    public SelectedOption(String groupCode, String optionCode) {
        this(OptionGroupId.of(groupCode), OptionId.of(optionCode));
    }

    public String groupCode() {
        return optionGroupId.value();
    }

    public String optionCode() {
        return optionId.value();
    }
}
