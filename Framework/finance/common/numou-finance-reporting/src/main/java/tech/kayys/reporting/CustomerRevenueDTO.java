package tech.kayys.reporting;

import java.math.BigDecimal;

public class CustomerRevenueDTO {
    private Long id;
    private String name;
    private BigDecimal revenue;
    
    public CustomerRevenueDTO() {}
    
    public CustomerRevenueDTO(Long id, String name, BigDecimal revenue) {
        this.id = id;
        this.name = name;
        this.revenue = revenue;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public BigDecimal getRevenue() {
        return revenue;
    }
    
    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }
}
