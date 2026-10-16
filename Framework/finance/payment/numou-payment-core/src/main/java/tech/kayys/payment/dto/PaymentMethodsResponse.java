package tech.kayys.payment.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

import tech.kayys.payment.gateway.GatewayProvider;
import tech.kayys.payment.method.PaymentMethodType;

/**
 * Response DTO containing available payment methods and gateways
 */
public class PaymentMethodsResponse {
    
    @JsonProperty("payment_methods")
    private List<PaymentMethodDTO> paymentMethods;
    
    @JsonProperty("gateway_providers")
    private List<GatewayProviderDTO> gatewayProviders;
    
    // Getters and Setters
    public List<PaymentMethodDTO> getPaymentMethods() {
        return paymentMethods;
    }
    
    public void setPaymentMethods(List<PaymentMethodDTO> paymentMethods) {
        this.paymentMethods = paymentMethods;
    }
    
    public List<GatewayProviderDTO> getGatewayProviders() {
        return gatewayProviders;
    }
    
    public void setGatewayProviders(List<GatewayProviderDTO> gatewayProviders) {
        this.gatewayProviders = gatewayProviders;
    }
    
    /**
     * Create from available payment methods and gateway providers
     */
    public static PaymentMethodsResponse fromLists(
        List<PaymentMethodType> methods, 
        List<GatewayProvider> providers) {
        
        PaymentMethodsResponse response = new PaymentMethodsResponse();
        
        response.setPaymentMethods(methods.stream()
            .map(PaymentMethodDTO::fromEnum)
            .collect(Collectors.toList()));
        
        response.setGatewayProviders(providers.stream()
            .map(GatewayProviderDTO::fromEnum)
            .collect(Collectors.toList()));
        
        return response;
    }
    
    /**
     * Payment Method DTO
     */
    public static class PaymentMethodDTO {
        private String code;
        private String name;
        private String type;
        private boolean qris;
        private boolean bankTransfer;
        private boolean eWallet;
        private boolean fintech;
        private boolean card;
        private boolean cashPayment;
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public boolean isQris() {
            return qris;
        }
        
        public void setQris(boolean qris) {
            this.qris = qris;
        }
        
        public boolean isBankTransfer() {
            return bankTransfer;
        }
        
        public void setBankTransfer(boolean bankTransfer) {
            this.bankTransfer = bankTransfer;
        }
        
        public boolean iseWallet() {
            return eWallet;
        }
        
        public void seteWallet(boolean eWallet) {
            this.eWallet = eWallet;
        }
        
        public boolean isFintech() {
            return fintech;
        }
        
        public void setFintech(boolean fintech) {
            this.fintech = fintech;
        }
        
        public boolean isCard() {
            return card;
        }
        
        public void setCard(boolean card) {
            this.card = card;
        }
        
        public boolean isCashPayment() {
            return cashPayment;
        }
        
        public void setCashPayment(boolean cashPayment) {
            this.cashPayment = cashPayment;
        }
        
        public static PaymentMethodDTO fromEnum(PaymentMethodType type) {
            PaymentMethodDTO dto = new PaymentMethodDTO();
            dto.setCode(type.name());
            dto.setName(type.getDisplayName());
            dto.setType(type.name().split("_")[0]);
            dto.setQris(type.isQRIS());
            dto.setBankTransfer(type.isBankTransfer());
            dto.seteWallet(type.isEWallet());
            dto.setFintech(type.isFintech());
            dto.setCard(type.isCard());
            dto.setCashPayment(type.isCashPayment());
            return dto;
        }
    }
    
    /**
     * Gateway Provider DTO
     */
    public static class GatewayProviderDTO {
        private String code;
        private String shortName;
        private String fullName;
        private String type;
        private boolean aggregator;
        private boolean bank;
        private boolean eWallet;
        private boolean qris;
        private boolean fintech;
        private boolean card;
        private boolean retail;
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getShortName() {
            return shortName;
        }
        
        public void setShortName(String shortName) {
            this.shortName = shortName;
        }
        
        public String getFullName() {
            return fullName;
        }
        
        public void setFullName(String fullName) {
            this.fullName = fullName;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public boolean isAggregator() {
            return aggregator;
        }
        
        public void setAggregator(boolean aggregator) {
            this.aggregator = aggregator;
        }
        
        public boolean isBank() {
            return bank;
        }
        
        public void setBank(boolean bank) {
            this.bank = bank;
        }
        
        public boolean iseWallet() {
            return eWallet;
        }
        
        public void seteWallet(boolean eWallet) {
            this.eWallet = eWallet;
        }
        
        public boolean isQris() {
            return qris;
        }
        
        public void setQris(boolean qris) {
            this.qris = qris;
        }
        
        public boolean isFintech() {
            return fintech;
        }
        
        public void setFintech(boolean fintech) {
            this.fintech = fintech;
        }
        
        public boolean isCard() {
            return card;
        }
        
        public void setCard(boolean card) {
            this.card = card;
        }
        
        public boolean isRetail() {
            return retail;
        }
        
        public void setRetail(boolean retail) {
            this.retail = retail;
        }
        
        public static GatewayProviderDTO fromEnum(GatewayProvider provider) {
            GatewayProviderDTO dto = new GatewayProviderDTO();
            dto.setCode(provider.name());
            dto.setShortName(provider.getShortName());
            dto.setFullName(provider.getFullName());
            dto.setType(provider.getType().name());
            dto.setAggregator(provider.isAggregator());
            dto.setBank(provider.isBank());
            dto.seteWallet(provider.isEWallet());
            dto.setQris(provider.isQRIS());
            dto.setFintech(provider.isFintech());
            dto.setCard(provider.isCard());
            dto.setRetail(provider.isRetail());
            return dto;
        }
    }
}
