package tech.kayys.sy.currency.core.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tech.kayys.sy.currency.core.service.ValidCurrencyCode;
import tech.kayys.sy.currency.model.ValidationGroups;

@ValidCurrencyCode(groups = { ValidationGroups.Create.class, ValidationGroups.Update.class })
public class CurrencyCreateDto {

    @NotBlank(groups = ValidationGroups.Create.class)
    @Size(min = 3, max = 3, groups = ValidationGroups.Create.class)
    public String code;

    @NotBlank(groups = ValidationGroups.Create.class)
    public String name;

    @NotBlank(groups = ValidationGroups.Create.class)
    public String symbol;

    @Min(value = 0, groups = ValidationGroups.Create.class)
    @Max(value = 8, groups = ValidationGroups.Create.class)
    public int decimalPlaces;

    public boolean active = true;
}