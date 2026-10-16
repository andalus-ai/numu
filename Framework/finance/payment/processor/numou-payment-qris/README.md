# Ky Payment QRIS Processor

QRIS (Quick Response Code Indonesian Standard) payment processor module for the Ky Payment Platform.

## Overview

This module provides comprehensive QRIS payment processing capabilities for the Indonesian market. QRIS is a standardized QR code payment system that supports multiple e-wallets and banking applications including:

- **GoPay**
- **OVO**
- **DANA**
- **LinkAja**
- **ShopeePay**
- **Mobile Banking apps** (BCA Mobile, Mandiri Mobile, BNI Mobile, etc.)

## Features

- **Multiple QRIS Channels**: Support for various QRIS variants (QRIS_DANA, QRIS_OVO, QRIS_GOPAY, QRIS_LINKAJA)
- **Dynamic QR Code Generation**: Generate QR codes using ZXing library
- **Payment Lifecycle Management**: Initiate, verify, cancel, and refund QRIS payments
- **Transaction Tracking**: Persistent storage of QRIS payment records
- **Configurable Limits**: Min/max amount validation
- **Expiry Management**: Automatic expiry time tracking
- **Statistics & Reporting**: Payment statistics and history

## Architecture

```
tech.kayys.payment.processor.qris/
├── QRISProcessor.java              # Main processor extending AbstractPaymentMethodProcessor
├── QRISCodeGenerator.java          # QR code generation utility
├── config/
│   └── QRISConfig.java             # Configuration properties interface
├── domain/
│   └── QRISPayment.java            # JPA entity for QRIS payments
├── repository/
│   └── QRISPaymentRepository.java  # Data access layer
└── service/
    └── QRIService.java             # Business logic service
```

## Dependencies

```xml
<dependency>
    <groupId>tech.kayys.payment</groupId>
    <artifactId>ky-payment-qris</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Third-party Dependencies

- **ZXing 3.5.2**: QR code generation library
- **Quarkus Hibernate ORM Panache**: Data persistence

## Configuration

Add the following configuration to your `application.properties`:

```properties
# QRIS Configuration
payment.qris.default-acquirer=MANDIRI
payment.qris.min-amount=1000
payment.qris.max-amount=10000000
payment.qris.expiry-minutes=1440
payment.qris.dynamic-qr=true
payment.qris.static-qr=false
payment.qris.qr-width=300
payment.qris.qr-height=300
payment.qris.validation-enabled=true
payment.qris.supported-channels=GOPAY,OVO,DANA,LINKAJA,SHOPEEPAY
payment.qris.webhook-enabled=true
payment.qris.webhook-secret=your-webhook-secret
```

### Configuration Options

| Property | Default | Description |
|----------|---------|-------------|
| `payment.qris.default-acquirer` | MANDIRI | Default acquirer bank for QRIS transactions |
| `payment.qris.min-amount` | 1000 | Minimum transaction amount (IDR) |
| `payment.qris.max-amount` | 10000000 | Maximum transaction amount (IDR) |
| `payment.qris.expiry-minutes` | 1440 | QR code expiry time in minutes (24 hours) |
| `payment.qris.dynamic-qr` | true | Enable dynamic QR code generation |
| `payment.qris.static-qr` | false | Enable static QR code support |
| `payment.qris.qr-width` | 300 | Default QR code image width |
| `payment.qris.qr-height` | 300 | Default QR code image height |
| `payment.qris.validation-enabled` | true | Enable QRIS validation |
| `payment.qris.supported-channels` | GOPAY,OVO,DANA,LINKAJA,SHOPEEPAY | Supported e-wallet channels |
| `payment.qris.webhook-enabled` | true | Enable webhook notifications |
| `payment.qris.webhook-secret` | - | Webhook signature verification secret |

## Usage

### Creating a QRIS Payment

```java
@Inject
QRISProcessor qrisProcessor;

// Create payment context
PaymentMethodContext context = new PaymentMethodContext();
context.setTransactionId("TXN" + System.currentTimeMillis());
context.setExternalOrderId("ORDER-12345");
context.setAmount(new BigDecimal("100000"));
context.setCurrency("IDR");
context.setCustomerName("John Doe");
context.setCustomerEmail("john@example.com");
context.setCustomerPhone("08123456789");
context.setPaymentMethod(PaymentMethodType.QRIS);
context.setGatewayProvider("MIDTRANS");
context.setCallbackUrl("https://your-domain.com/callback");

// Initiate payment
PaymentMethodResult result = qrisProcessor.initiate(context);

if (result.isSuccess()) {
    String qrCodeString = result.getQrCodeString();
    String qrCodeUrl = result.getQrCodeUrl();
    
    // Generate QR code image
    byte[] qrCodeImage = QRISCodeGenerator.generateQRCode(qrCodeString);
    
    // Return to customer for scanning
} else {
    // Handle error
    String errorCode = result.getErrorCode();
    String errorMessage = result.getErrorMessage();
}
```

### Specific QRIS Channel

```java
// Use specific e-wallet channel
context.setPaymentMethod(PaymentMethodType.QRIS_GOPAY);
// or
context.setPaymentMethod(PaymentMethodType.QRIS_OVO);
// or
context.setPaymentMethod(PaymentMethodType.QRIS_DANA);
```

### Verifying Payment Status

```java
// Verify payment status
PaymentMethodResult result = qrisProcessor.verify(transactionId);

if (result.getStatus() == PaymentMethodResult.PaymentStatus.SUCCESS) {
    // Payment completed successfully
} else if (result.getStatus() == PaymentMethodResult.PaymentStatus.PENDING) {
    // Payment still pending
}
```

### Cancelling Payment

```java
// Cancel pending payment
PaymentMethodResult result = qrisProcessor.cancel(transactionId, "Customer requested cancellation");
```

### Refunding Payment

```java
// Full refund
PaymentMethodResult result = qrisProcessor.refund(transactionId, null, "Product return");

// Partial refund
PaymentMethodResult result = qrisProcessor.refund(
    transactionId, 
    new BigDecimal("50000"), 
    "Partial product return"
);
```

### Using QRIService

```java
@Inject
QRIService qrisService;

// Get payment details
Optional<QRISPayment> payment = qrisService.getPayment(transactionId);

// Get payments by status
List<QRISPayment> pendingPayments = qrisService.getPendingPayments();
List<QRISPayment> successfulPayments = qrisService.getSuccessfulPayments();

// Get payment statistics
QRIService.QRISPaymentStats stats = qrisService.getStats();
long totalPayments = stats.getTotalPayments();
double successRate = stats.getSuccessRate();

// Get customer payment history
List<QRISPayment> customerPayments = qrisService.getCustomerPayments("customer@email.com");

// Mark expired payments
int expiredCount = qrisService.markExpiredPayments();

// Cleanup old expired payments
int deletedCount = qrisService.cleanupExpiredPayments(30); // Delete older than 30 days
```

### Generating QR Code Images

```java
// Generate QR code as byte array (PNG)
byte[] qrCodeImage = QRISCodeGenerator.generateQRCode(qrCodeString, 300, 300);

// Generate with default dimensions
byte[] qrCodeImage = QRISCodeGenerator.generateQRCode(qrCodeString);

// Generate as base64 string (for web display)
String base64Image = QRISCodeGenerator.generateQRCodeBase64(qrCodeString);

// Validate QRIS code format
boolean isValid = QRISCodeGenerator.isValidQRISCode(qrCodeString);
```

## Payment Status Flow

```
PENDING → SUCCESS    (Payment completed)
        → FAILED     (Payment failed)
        → CANCELLED  (Payment cancelled by user/merchant)
        → EXPIRED    (QR code expired without payment)
SUCCESS → REFUNDED   (Full refund)
        → PARTIALLY_REFUNDED (Partial refund)
```

## Database Schema

The `QRISPayment` entity is stored in the `qris_payments` table:

```sql
CREATE TABLE qris_payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id VARCHAR(255) UNIQUE NOT NULL,
    external_order_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    customer_phone VARCHAR(50),
    payment_method VARCHAR(50) NOT NULL,
    gateway_provider VARCHAR(50),
    external_transaction_id VARCHAR(255),
    qr_code_string TEXT,
    qr_code_url TEXT,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    error_code VARCHAR(50),
    expiry_time TIMESTAMP,
    paid_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancel_reason TEXT,
    refunded_at TIMESTAMP,
    refund_reason TEXT,
    refund_amount DECIMAL(19,2),
    created_at TIMESTAMP NOT NULL,
    last_updated_at TIMESTAMP
);
```

## Testing

Run tests using Maven:

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=QRISProcessorTest

# Run with coverage
mvn test jacoco:report
```

### Test Classes

- `QRISProcessorTest` - Integration tests for QRIS processor
- `QRISCodeGeneratorTest` - Unit tests for QR code generation

## Error Codes

| Error Code | Description |
|------------|-------------|
| `INVALID_AMOUNT` | Amount is null, zero, or negative |
| `AMOUNT_BELOW_MINIMUM` | Amount below configured minimum |
| `AMOUNT_EXCEEDS_MAXIMUM` | Amount exceeds configured maximum |
| `INVALID_PAYMENT_METHOD` | Non-QRIS payment method used |
| `GATEWAY_NOT_FOUND` | Configured gateway provider not available |
| `TRANSACTION_NOT_FOUND` | Transaction ID not found during verification |
| `CANCEL_FAILED` | Failed to cancel transaction |
| `REFUND_FAILED` | Failed to refund transaction |
| `CANNOT_CANCEL_COMPLETED` | Attempting to cancel completed payment |
| `CANNOT_REFUND_INCOMPLETE` | Attempting to refund non-completed payment |
| `REFUND_AMOUNT_EXCEEDS` | Refund amount exceeds original payment |

## Best Practices

1. **Amount Validation**: Always validate amounts before initiating payment
2. **Expiry Handling**: Implement automatic expiry checking for pending payments
3. **Webhook Verification**: Always verify webhook signatures for security
4. **Idempotency**: Use unique transaction IDs to prevent duplicate payments
5. **Error Handling**: Log all errors and provide meaningful error messages to users
6. **QR Code Display**: Ensure QR codes are displayed at appropriate size for scanning
7. **Status Polling**: Implement periodic status checks for pending payments
8. **Cleanup**: Regularly clean up expired payment records

## Security Considerations

1. **Webhook Security**: Verify webhook signatures using configured secret
2. **Transaction Validation**: Validate all transaction parameters before processing
3. **Amount Limits**: Enforce min/max amount limits to prevent fraud
4. **Audit Logging**: Log all payment operations for audit trail
5. **Data Protection**: Encrypt sensitive customer data at rest

## Troubleshooting

### QR Code Not Scanning

- Ensure QR code is displayed at sufficient size (minimum 200x200 pixels)
- Check QR code string format is valid QRIS standard
- Verify screen brightness is adequate for scanning

### Payment Not Completing

- Check gateway provider configuration
- Verify webhook endpoint is accessible
- Review gateway provider logs for errors

### Amount Validation Errors

- Check configured min/max amounts in application.properties
- Verify amount is in IDR (Indonesian Rupiah)

## License

Proprietary - Numou Platform

## Support

For support and questions, contact the Numou Platform development team.
