# Ky Payment Core - Modular Payment System

A comprehensive, modular payment processing system for Indonesian market with support for multiple payment methods and gateway providers.

## Features

### Supported Payment Methods

#### Bank Transfer
- **Virtual Accounts**: BCA, Mandiri, BNI, BRI, Permata
- **Direct Transfer**: Standard bank transfer

#### QRIS (Quick Response Code Indonesian Standard)
- **QRIS Standard**: Universal QR code
- **QRIS via DANA**: DANA QRIS
- **QRIS via OVO**: OVO QRIS
- **QRIS via GoPay**: GoPay QRIS
- **QRIS via LinkAja**: LinkAja QRIS

#### E-Wallets
- OVO
- GoPay
- DANA
- LinkAja
- ShopeePay

#### Fintech / Paylater
- Kredivo
- Akulaku
- Indodana

#### Cards
- Credit Card (Visa, Mastercard, JCB, Amex)
- Debit Card

#### Retail / Over-the-Counter
- Alfamart
- Indomaret

### Supported Gateway Providers

#### Payment Aggregators
- **Midtrans**: Full-service payment aggregator
- **Xendit**: Payment gateway with extensive API
- **Doku**: Indonesian payment gateway
- **OY! Indonesia**: Bank transfer specialist

#### Direct Banks
- BCA (Bank Central Asia)
- Mandiri
- BNI (Bank Negara Indonesia)
- BRI (Bank Rakyat Indonesia)
- Permata
- Danamon
- CIMB Niaga
- OCBC NISP
- BSI (Bank Syariah Indonesia)

#### E-Wallet Providers
- OVO
- GoPay
- DANA
- LinkAja
- ShopeePay

#### QRIS Providers
- QRIS Midtrans
- QRIS Xendit
- QRIS Doku
- QRIS BI (Bank Indonesia)

#### Fintech Providers
- Kredivo
- Akulaku
- Indodana

#### Card Networks
- Visa
- Mastercard
- JCB
- American Express

#### Retail
- Alfamart
- Indomaret

## Architecture

### Design Patterns

1. **Strategy Pattern**: Payment method processors and gateway providers
2. **Factory Pattern**: Payment processor factory for dynamic selection
3. **Registry Pattern**: Centralized management of processors and gateways
4. **Adapter Pattern**: Gateway provider adapters for uniform interface

### Package Structure

```
tech.kayys.payment/
├── Payment.java                          # Payment entity
├── PaymentRefund.java                    # Refund entity
├── PaymentMethodType.java                # Payment method enumeration
├── PaymentRepository.java                # Data access layer
├── PaymentService.java                   # Business logic orchestration
├── PaymentResource.java                  # REST API endpoints
├── dto/
│   ├── CreatePaymentRequest.java        # Payment creation request
│   ├── PaymentResponse.java             # Payment response DTO
│   ├── RefundRequest.java               # Refund request DTO
│   ├── RefundResponse.java              # Refund response DTO
│   └── PaymentMethodsResponse.java      # Available methods response
├── method/
│   ├── PaymentMethodProcessor.java      # Processor interface
│   ├── PaymentMethodContext.java        # Processing context
│   ├── PaymentMethodResult.java         # Processing result
│   ├── PaymentMethodType.java           # Method type enum
│   └── processor/
│       ├── AbstractPaymentMethodProcessor.java
│       ├── BankTransferProcessor.java
│       ├── QRISProcessor.java
│       ├── EWalletProcessor.java
│       ├── FintechProcessor.java
│       ├── CardProcessor.java
│       └── RetailProcessor.java
├── gateway/
│   ├── PaymentGatewayProvider.java      # Gateway interface
│   ├── GatewayProvider.java             # Provider enum
│   ├── GatewayType.java                 # Gateway type enum
│   ├── GatewayRequest.java              # Gateway request
│   ├── GatewayResponse.java             # Gateway response
│   ├── GatewayConfig.java               # Gateway configuration
│   └── provider/
│       ├── MidtransClient.java          # Midtrans REST client
│       ├── MidtransGatewayProvider.java # Midtrans implementation
│       ├── XenditClient.java            # Xendit REST client
│       └── XenditGatewayProvider.java   # Xendit implementation
└── service/
    ├── PaymentGatewayRegistry.java      # Gateway registry
    ├── PaymentMethodRegistry.java       # Method registry
    └── PaymentProcessorFactory.java     # Processor factory
```

## Usage

### Creating a Payment

```java
// Create payment request
CreatePaymentRequest request = new CreatePaymentRequest();
request.setExternalOrderId("ORDER-12345");
request.setPaymentMethod("QRIS");
request.setAmount(new BigDecimal("100000"));
request.setCustomerName("John Doe");
request.setCustomerEmail("john@example.com");
request.setCustomerPhone("08123456789");
request.setGatewayProvider("MIDTRANS"); // Optional, auto-selected if not specified

// Create and process payment
PaymentResponse response = paymentService.createAndProcessPayment(request);

// Response contains payment details
String paymentUrl = response.getPaymentUrl();      // For redirect payments
String qrCodeString = response.getQrCodeString();  // For QRIS
String vaNumber = response.getVirtualAccountNumber(); // For bank transfer
```

### Payment Methods API

```bash
# Get all available payment methods
GET /api/payments/methods

# Get payment methods by type
GET /api/payments/methods/qris
GET /api/payments/methods/bank_transfer
GET /api/payments/methods/e_wallet
GET /api/payments/methods/fintech
```

### Payment Operations

```bash
# Get payment by ID
GET /api/payments/{id}

# Get payment by transaction ID
GET /api/payments/transaction/{transactionId}

# Get payments by invoice
GET /api/payments/invoice/{invoiceId}

# Get pending payments
GET /api/payments/pending

# Create payment
POST /api/payments
Content-Type: application/json

{
  "external_order_id": "ORDER-123",
  "payment_method": "QRIS",
  "gateway_provider": "MIDTRANS",
  "amount": 100000,
  "currency": "IDR",
  "customer_name": "John Doe",
  "customer_email": "john@example.com",
  "customer_phone": "08123456789",
  "callback_url": "https://your-domain.com/callback",
  "metadata": {
    "order_items": ["Item 1", "Item 2"]
  }
}

# Verify payment status
PUT /api/payments/{id}/verify

# Cancel payment
PUT /api/payments/{id}/cancel
Content-Type: application/json

{
  "reason": "Customer requested cancellation"
}

# Refund payment
POST /api/payments/{id}/refund
Content-Type: application/json

{
  "amount": 100000,  // Optional, full refund if not specified
  "reason": "Product return"
}
```

### Webhook Handling

```bash
# Gateway webhook endpoint
POST /api/payments/webhook/{provider}?signature={signature}
```

## Configuration

Copy `application.properties.example` to your resources folder and configure:

```properties
# Midtrans
payment.gateway.midtrans.server-key=YOUR_SERVER_KEY
payment.gateway.midtrans.environment=SANDBOX

# Xendit
payment.gateway.xendit.secret-key=YOUR_SECRET_KEY
payment.gateway.xendit.environment=SANDBOX

# Default settings
payment.default.gateway=MIDTRANS
payment.currency=IDR
```

## Adding New Payment Methods

1. Add new enum value to `PaymentMethodType`
2. Create processor class extending `AbstractPaymentMethodProcessor`
3. Register processor (automatic via CDI)

```java
@ApplicationScoped
public class NewMethodProcessor extends AbstractPaymentMethodProcessor {
    @Override
    public PaymentMethodType getPaymentMethodType() {
        return PaymentMethodType.NEW_METHOD;
    }
    
    @Override
    protected GatewayRequest buildGatewayRequest(PaymentMethodContext context) {
        // Build gateway-specific request
    }
}
```

## Adding New Gateway Providers

1. Add new enum value to `GatewayProvider`
2. Implement `PaymentGatewayProvider` interface
3. Create REST client for gateway API
4. Register provider (automatic via CDI)

```java
@ApplicationScoped
public class NewGatewayProvider implements PaymentGatewayProvider {
    @Override
    public GatewayProvider getProvider() {
        return GatewayProvider.NEW_GATEWAY;
    }
    
    @Override
    public GatewayResponse createPayment(GatewayRequest request) {
        // Implement payment creation
    }
    
    // Implement other methods...
}
```

## Dependencies

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-orm-panache</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-client-reactive-jackson</artifactId>
</dependency>
```

## Testing

```bash
# Run tests
mvn test

# Run with specific gateway (sandbox)
mvn test -Dsandbox=true
```

## Security Considerations

1. **API Keys**: Store in secure vault or environment variables
2. **Webhook Verification**: Always verify webhook signatures
3. **PCI DSS**: For card payments, ensure PCI DSS compliance
4. **Data Encryption**: Encrypt sensitive payment data at rest
5. **Audit Logging**: Log all payment operations for audit trail

## License

Proprietary - Numou Platform
