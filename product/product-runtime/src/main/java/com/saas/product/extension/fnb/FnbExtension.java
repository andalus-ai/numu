package com.saas.product.extension.fnb;

import com.saas.product.core.model.Money;
import com.saas.product.spi.ProductExtension;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Food & Beverage domain extension.
 *
 * Covers restaurant, café, cloud kitchen, catering:
 *  - Dine-in / takeaway / delivery pricing (different per channel)
 *  - Modifier groups (add-ons, variants like size/spice level)
 *  - Preparation time
 *  - Dietary tags (halal, vegan, gluten-free, etc.)
 *  - Allergens
 *  - Kitchen station routing
 *  - Availability schedule (lunch/dinner/weekend menus)
 */
public final class FnbExtension implements ProductExtension {

    public static final String CONTEXT = "fnb";

    private final Money dineInPrice;
    private final Money takeawayPrice;    // null = same as dineIn
    private final Money deliveryPrice;   // null = same as dineIn
    private final List<ModifierGroup> modifierGroups;
    private final int preparationTimeMinutes;
    private final List<String> dietaryTags;     // "halal", "vegan", "vegetarian", etc.
    private final List<String> allergens;       // "gluten", "nuts", "dairy", etc.
    private final String kitchenStation;        // "grill", "cold-prep", "bar", "pizza"
    private final boolean availableForDineIn;
    private final boolean availableForTakeaway;
    private final boolean availableForDelivery;
    private final AvailabilitySchedule schedule;

    private FnbExtension(Builder b) {
        this.dineInPrice             = Objects.requireNonNull(b.dineInPrice, "dineInPrice");
        this.takeawayPrice           = b.takeawayPrice;
        this.deliveryPrice           = b.deliveryPrice;
        this.modifierGroups          = b.modifierGroups != null
                ? Collections.unmodifiableList(b.modifierGroups) : List.of();
        this.preparationTimeMinutes  = Math.max(0, b.preparationTimeMinutes);
        this.dietaryTags             = b.dietaryTags != null
                ? Collections.unmodifiableList(b.dietaryTags) : List.of();
        this.allergens               = b.allergens != null
                ? Collections.unmodifiableList(b.allergens) : List.of();
        this.kitchenStation          = b.kitchenStation;
        this.availableForDineIn      = b.availableForDineIn;
        this.availableForTakeaway    = b.availableForTakeaway;
        this.availableForDelivery    = b.availableForDelivery;
        this.schedule                = b.schedule;
    }

    @Override
    public String getContext() { return CONTEXT; }

    @Override
    public String describe() {
        return "FnbExtension{dineIn=" + dineInPrice
                + ", modifiers=" + modifierGroups.size()
                + ", prepTime=" + preparationTimeMinutes + "m}";
    }

    // ---- Business queries ----

    public Money priceForChannel(String channel) {
        return switch (channel.toLowerCase()) {
            case "takeaway" -> takeawayPrice != null ? takeawayPrice : dineInPrice;
            case "delivery" -> deliveryPrice != null ? deliveryPrice : dineInPrice;
            default         -> dineInPrice;
        };
    }

    public boolean isHalal()      { return dietaryTags.contains("halal"); }
    public boolean isVegan()      { return dietaryTags.contains("vegan"); }
    public boolean isVegetarian() { return dietaryTags.contains("vegetarian"); }

    public boolean isAvailableFor(String channel) {
        return switch (channel.toLowerCase()) {
            case "takeaway" -> availableForTakeaway;
            case "delivery" -> availableForDelivery;
            default         -> availableForDineIn;
        };
    }

    // ---- Getters ----

    public Money getDineInPrice()           { return dineInPrice; }
    public Money getTakeawayPrice()         { return takeawayPrice; }
    public Money getDeliveryPrice()         { return deliveryPrice; }
    public List<ModifierGroup> getModifierGroups() { return modifierGroups; }
    public int getPreparationTimeMinutes()  { return preparationTimeMinutes; }
    public List<String> getDietaryTags()    { return dietaryTags; }
    public List<String> getAllergens()       { return allergens; }
    public String getKitchenStation()       { return kitchenStation; }
    public boolean isAvailableForDineIn()   { return availableForDineIn; }
    public boolean isAvailableForTakeaway() { return availableForTakeaway; }
    public boolean isAvailableForDelivery() { return availableForDelivery; }
    public AvailabilitySchedule getSchedule() { return schedule; }

    // ---- Builder ----

    public static Builder builder(Money dineInPrice) {
        return new Builder(dineInPrice);
    }

    public static final class Builder {
        private final Money dineInPrice;
        private Money takeawayPrice;
        private Money deliveryPrice;
        private List<ModifierGroup> modifierGroups;
        private int preparationTimeMinutes = 15;
        private List<String> dietaryTags;
        private List<String> allergens;
        private String kitchenStation;
        private boolean availableForDineIn   = true;
        private boolean availableForTakeaway = true;
        private boolean availableForDelivery = true;
        private AvailabilitySchedule schedule;

        private Builder(Money dineInPrice) { this.dineInPrice = dineInPrice; }

        public Builder takeawayPrice(Money m)              { this.takeawayPrice = m; return this; }
        public Builder deliveryPrice(Money m)              { this.deliveryPrice = m; return this; }
        public Builder modifierGroups(List<ModifierGroup> g) { this.modifierGroups = g; return this; }
        public Builder preparationTimeMinutes(int t)       { this.preparationTimeMinutes = t; return this; }
        public Builder dietaryTags(List<String> t)         { this.dietaryTags = t; return this; }
        public Builder allergens(List<String> a)           { this.allergens = a; return this; }
        public Builder kitchenStation(String s)            { this.kitchenStation = s; return this; }
        public Builder availableForDineIn(boolean b)       { this.availableForDineIn = b; return this; }
        public Builder availableForTakeaway(boolean b)     { this.availableForTakeaway = b; return this; }
        public Builder availableForDelivery(boolean b)     { this.availableForDelivery = b; return this; }
        public Builder schedule(AvailabilitySchedule s)    { this.schedule = s; return this; }

        public FnbExtension build() { return new FnbExtension(this); }
    }

    // ── Nested types ─────────────────────────────────────────────────────

    /**
     * A named group of options the customer can choose from.
     * e.g. "Size" (required, pick 1), "Extras" (optional, pick up to 3)
     */
    public record ModifierGroup(
            String id,
            String name,
            boolean required,
            int minSelect,
            int maxSelect,
            List<ModifierOption> options
    ) {}

    /**
     * A single selectable option within a modifier group.
     * e.g. "Large +2000", "Extra shot +5000"
     */
    public record ModifierOption(
            String id,
            String name,
            Money additionalPrice,
            boolean isDefault
    ) {}

    /**
     * Time-based availability windows.
     * e.g. breakfast menu only available 07:00–11:00
     */
    public record AvailabilitySchedule(
            String startTime,   // "HH:mm" 24h
            String endTime,
            List<Integer> daysOfWeek  // 1=Mon, 7=Sun (ISO)
    ) {
        public boolean isAlwaysAvailable() {
            return startTime == null && endTime == null;
        }
    }
}
