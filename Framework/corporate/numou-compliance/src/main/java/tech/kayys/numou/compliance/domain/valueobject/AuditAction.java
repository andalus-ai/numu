package tech.kayys.syirkah.compliance.domain.valueobject;

/**
 * Types of audit actions.
 */
public enum AuditAction {
    // User Management
    USER_LOGIN("User Login"),
    USER_LOGOUT("User Logout"),
    USER_CREATED("User Created"),
    USER_UPDATED("User Updated"),
    USER_DELETED("User Deleted"),
    USER_ACTIVATED("User Activated"),
    USER_DEACTIVATED("User Deactivated"),
    USER_LOCKED("User Locked"),
    USER_UNLOCKED("User Unlocked"),
    
    // Tenant/Company Management
    TENANT_CREATED("Tenant Created"),
    TENANT_UPDATED("Tenant Updated"),
    TENANT_ACTIVATED("Tenant Activated"),
    TENANT_SUSPENDED("Tenant Suspended"),
    COMPANY_CREATED("Company Created"),
    COMPANY_UPDATED("Company Updated"),
    
    // Data Operations
    DATA_CREATED("Data Created"),
    DATA_UPDATED("Data Updated"),
    DATA_DELETED("Data Deleted"),
    DATA_VIEWED("Data Viewed"),
    DATA_EXPORTED("Data Exported"),
    DATA_IMPORTED("Data Imported"),
    
    // Security
    PASSWORD_CHANGED("Password Changed"),
    PASSWORD_RESET("Password Reset"),
    MFA_ENABLED("MFA Enabled"),
    MFA_DISABLED("MFA Disabled"),
    PERMISSION_CHANGED("Permission Changed"),
    ROLE_CHANGED("Role Changed"),
    
    // System Operations
    SYSTEM_STARTUP("System Startup"),
    SYSTEM_SHUTDOWN("System Shutdown"),
    SYSTEM_CONFIGURATION("System Configuration Changed"),
    BACKUP_CREATED("Backup Created"),
    BACKUP_RESTORED("Backup Restored"),
    
    // Financial
    PAYMENT_PROCESSED("Payment Processed"),
    INVOICE_CREATED("Invoice Created"),
    INVOICE_PAID("Invoice Paid"),
    REFUND_PROCESSED("Refund Processed");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
