package tech.kayys.payment.gateway;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Request object for payment gateway operations
 */
public class GatewayRequest {
    private String transactionId;
    private String externalOrderId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private CustomerInfo customer;
    private ItemInfo[] items;
    private String callbackUrl;
    private String returnUrl;
    private Map<String, Object> metadata;
    private String description;
    
    public GatewayRequest() {
        this.currency = "IDR";
        this.metadata = new HashMap<>();
    }
    
    // Getters and Setters
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    public String getExternalOrderId() {
        return externalOrderId;
    }
    
    public void setExternalOrderId(String externalOrderId) {
        this.externalOrderId = externalOrderId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public CustomerInfo getCustomer() {
        return customer;
    }
    
    public void setCustomer(CustomerInfo customer) {
        this.customer = customer;
    }
    
    public ItemInfo[] getItems() {
        return items;
    }
    
    public void setItems(ItemInfo[] items) {
        this.items = items;
    }
    
    public String getCallbackUrl() {
        return callbackUrl;
    }
    
    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }
    
    public String getReturnUrl() {
        return returnUrl;
    }
    
    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    // Inner classes for customer and item info
    public static class CustomerInfo {
        private String id;
        private String name;
        private String email;
        private String phone;
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public String getPhone() {
            return phone;
        }
        
        public void setPhone(String phone) {
            this.phone = phone;
        }
    }
    
    public static class ItemInfo {
        private String id;
        private String name;
        private BigDecimal price;
        private Integer quantity;
        private String category;
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public BigDecimal getPrice() {
            return price;
        }
        
        public void setPrice(BigDecimal price) {
            this.price = price;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
        
        public String getCategory() {
            return category;
        }
        
        public void setCategory(String category) {
            this.category = category;
        }
    }
}
