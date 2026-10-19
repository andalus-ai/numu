package tech.kayys.numou.foundation.kernel.deployment;

/**
 * Defines the business distribution profile packaged for deployment.
 */
public enum DistributionProfile {

    /**
     * Minimal Point of Sale only (checkout, cash drawer, receipt printing).
     */
    POINT_OF_SALE,

    /**
     * F&B / Coffee shop distribution (POS, table/order management, recipe/inventory light).
     */
    COFFEE_SHOP,

    /**
     * Retail distribution (POS, inventory, procurement, supplier).
     */
    RETAIL,

    /**
     * Construction project management (projects, work breakdown, construction, budget, contracts).
     */
    CONSTRUCTION,

    /**
     * Complete enterprise ERP encompassing all bounded contexts.
     */
    ENTERPRISE_ERP
}
