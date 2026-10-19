# Ky Payment Bank Transfer Processor

Bank Transfer payment processor module for the Ky Payment Platform, supporting Indonesian banks with Virtual Account (VA) and standard bank transfer capabilities.

## Overview

This module provides comprehensive Bank Transfer payment processing capabilities for the Indonesian market, supporting multiple major banks:

- **BCA** (Bank Central Asia) - Virtual Account
- **Mandiri** - Virtual Account
- **BNI** (Bank Negara Indonesia) - Virtual Account
- **BRI** (Bank Rakyat Indonesia) - Virtual Account
- **Permata** - Virtual Account
- **Standard Bank Transfer** - Direct bank transfer

## Features

- **Multiple Bank Channels**: Support for VA BCA, VA Mandiri, VA BNI, VA BRI, VA Permata
- **Virtual Account Generation**: Automatic VA number assignment per transaction
- **Payment Lifecycle Management**: Initiate, verify, cancel, and refund bank transfer payments
- **Transaction Tracking**: Persistent storage of bank transfer payment records
- **Configurable Limits**: Min/max amount validation per bank
- **Expiry Management**: Automatic expiry time tracking for VA payments
- **Statistics & Reporting**: Payment statistics by bank and overall
- **Auto-Polling**: Automatic status polling for pending payments

## Architecture

```
tech.kayys.payment.processor.bank/
├── BankTransferProcessor.java        # Main processor extending AbstractPaymentMethodProcessor
├── config/
│   └── BankTransferConfig.java       # Configuration properties interface
├── domain/
│   └── BankTransferPayment.java      # JPA entity for bank transfer payments
├── repository/
│   └── BankTransferPaymentRepository.java  # Data access layer
└── service/
    └── BankTransferService.java      # Business logic service
```

## Dependencies

```xml
<dependency>
    <groupId>tech.kayys.payment</groupId>
    <artifactId>ky-payment-bank</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Third-party Dependencies

- **Quarkus Hibernate ORM Panache**: Data persistence

## Configuration

Add the following configuration to your `application.properties`:

```properties
# Bank Transfer Configuration
payment.bank.default-bank=BCA
payment.bank.min-amount=1000
payment.bank.max-amount=50000000
payment.bank.expiry-minutes=1440
payment.bank.va-enabled=true
payment.bank.transfer-enabled=true
payment.bank.validation-enabled=true
payment.bank.supported-banks=BCA,MANDIRI,BNI,BRI,PERMATA
payment.bank.webhook-enabled=true
payment.bank.webhook-secret=your-webhook-secret

# VA Number Prefixes (bank-specific)
payment.bank.bca.va-prefix=70013
payment.bank.mandiri.va-prefix=89008
payment.bank.bni.va-prefix=8881
payment.bank.bri.va-prefix=20107
payment.bank.permata.va-prefix=90010

# Auto-polling Configuration
payment.bank.auto-poll-enabled=true
payment.bank.poll-interval-seconds=60
```

### Configuration Options

| Property | Default | Description |
|----------|---------|-------------|
| `payment.bank.default-bank` | BCA | Default bank for bank transfer transactions |
| `payment.bank.min-amount` | 1000 | Minimum transaction amount (IDR) |
| `payment.bank.max-amount` | 50000000 | Maximum transaction amount (IDR) |
| `payment.bank.expiry-minutes` | 1440 | VA expiry time in minutes (24 hours) |
| `payment.bank.va-enabled` | true | Enable virtual account payments |
| `payment.bank.transfer-enabled` | true | Enable standard bank transfer |
| `payment.bank.validation-enabled` | true | Enable bank transfer validation |
| `payment.bank.supported-banks` | BCA,MANDIRI,BNI,BRI,PERMATA | Supported banks |
| `payment.bank.webhook-enabled` | true | Enable webhook notifications |
| `payment.bank.webhook-secret` | - | Webhook signature verification secret |
| `payment.bank.bca.va-prefix` | 70013 | VA number prefix for BCA |
| `payment.bank.mandiri.va-prefix` | 89008 | VA number prefix for Mandiri |
| `payment.bank.bni.va-prefix` | 8881 | VA number prefix for BNI |
| `payment.bank.bri.va-prefix` | 20107 | VA number prefix for BRI |
| `payment.bank.permata.va-prefix` | 90010 | VA number prefix for Permata |
| `payment.bank.auto-poll-enabled` | true | Enable automatic status polling |
| `payment.bank.poll-interval-seconds` | 60 | Polling interval in seconds |

## Usage

### Creating a Bank Transfer Payment

```java
@Inject
BankTransferProcessor bankTransferProcessor;

// Create payment context for VA BCA
PaymentMethodContext context = new PaymentMethodContext();
context.setTransactionId("TXN" + System.currentTimeMillis());
context.setExternalOrderId("ORDER-12345");
context.setAmount(new BigDecimal("100000"));
context.setCurrency("IDR");
context.setCustomerName("John Doe");
context.setCustomerEmail("john@example.com");
context.setCustomerPhone("08123456789");
context.setPaymentMethod(PaymentMethodType.VA_BCA);
context.setGatewayProvider("MIDTRANS");
context.setCallbackUrl("https://your-domain.com/callback");

// Initiate payment
PaymentMethodResult result = bankTransferProcessor.initiate(context);

if (result.isSuccess()) {
    String vaNumber = result.getVirtualAccountNumber();
    String bankCode = result.getBankCode();
    
    // Display VA number to customer for payment
    System.out.println("Please transfer to VA Number: " + vaNumber);
    System.out.println("Bank: " + bankCode);
} else {
    // Handle error
    String errorCode = result.getErrorCode();
    String errorMessage = result.getErrorMessage();
}
```

### Specific Bank VA

```java
// Use specific bank VA
context.setPaymentMethod(PaymentMethodType.VA_MANDIRI);
// or
context.setPaymentMethod(PaymentMethodType.VA_BNI);
// or
context.setPaymentMethod(PaymentMethodType.VA_BRI);
// or
context.setPaymentMethod(PaymentMethodType.VA_PERMATA);
```

### Standard Bank Transfer

```java
// Use standard bank transfer (non-VA)
context.setPaymentMethod(PaymentMethodType.BANK_TRANSFER);
```

### Verifying Payment Status

```java
// Verify payment status
PaymentMethodResult result = bankTransferProcessor.verify(transactionId);

if (result.getStatus() == PaymentMethodResult.PaymentStatus.SUCCESS) {
    // Payment completed successfully
} else if (result.getStatus() == PaymentMethodResult.PaymentStatus.PENDING) {
    // Payment still pending - customer hasn't transferred yet
}
```

### Cancelling Payment

```java
// Cancel pending payment
PaymentMethodResult result = bankTransferProcessor.cancel(
    transactionId, 
    "Customer requested cancellation"
);
```

### Refunding Payment

```java
// Full refund
PaymentMethodResult result = bankTransferProcessor.refund(
    transactionId, 
    null, 
    "Product return"
);

// Partial refund
PaymentMethodResult result = bankTransferProcessor.refund(
    transactionId, 
    new BigDecimal("50000"), 
    "Partial product return"
);
```

### Using BankTransferService

```java
@Inject
BankTransferService bankTransferService;

// Get payment details
Optional<BankTransferPayment> payment = bankTransferService.getPayment(transactionId);

// Get payment by VA number
Optional<BankTransferPayment> payment = bankTransferService.getPaymentByVANumber(vaNumber);

// Get payments by status
List<BankTransferPayment> pendingPayments = bankTransferService.getPendingPayments();
List<BankTransferPayment> successfulPayments = bankTransferService.getSuccessfulPayments();

// Get payment statistics
BankTransferService.BankTransferPaymentStats stats = bankTransferService.getStats();
long totalPayments = stats.getTotalPayments();
double successRate = stats.getSuccessRate();

// Get statistics by bank
BankTransferService.BankTransferPaymentStats bcaStats = bankTransferService.getStatsByBank("BCA");

// Get customer payment history
List<BankTransferPayment> customerPayments = bankTransferService.getCustomerPayments("customer@email.com");

// Get payments by bank
List<BankTransferPayment> bcaPayments = bankTransferService.getPaymentsByBank("BCA");

// Mark expired payments
int expiredCount = bankTransferService.markExpiredPayments();

// Cleanup old expired payments
int deletedCount = bankTransferService.cleanupExpiredPayments(30); // Delete older than 30 days
```

## Payment Status Flow

```
PENDING → SUCCESS    (Payment completed - customer transferred)
        → FAILED     (Payment failed)
        → CANCELLED  (Payment cancelled by user/merchant)
        → EXPIRED    (VA expired without payment)
SUCCESS → REFUNDED   (Full refund)
        → PARTIALLY_REFUNDED (Partial refund)
```

## Database Schema

The `BankTransferPayment` entity is stored in the `bank_transfer_payments` table:

```sql
CREATE TABLE bank_transfer_payments (
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
    virtual_account_number VARCHAR(50),
    bank_code VARCHAR(20),
    bank_name VARCHAR(50),
    account_number VARCHAR(50),
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
mvn test -Dtest=BankTransferProcessorTest

# Run with coverage
mvn test jacoco:report
```

### Test Classes

- `BankTransferProcessorTest` - Integration tests for Bank Transfer processor

## Error Codes

| Error Code | Description |
|------------|-------------|
| `INVALID_AMOUNT` | Amount is null, zero, or negative |
| `AMOUNT_BELOW_MINIMUM` | Amount below configured minimum (1,000 IDR) |
| `AMOUNT_EXCEEDS_MAXIMUM` | Amount exceeds configured maximum (50M IDR) |
| `INVALID_PAYMENT_METHOD` | Non-Bank Transfer payment method used |
| `GATEWAY_NOT_FOUND` | Configured gateway provider not available |
| `TRANSACTION_NOT_FOUND` | Transaction ID not found during verification |
| `CANCEL_FAILED` | Failed to cancel transaction |
| `REFUND_FAILED` | Failed to refund transaction |
| `CANNOT_CANCEL_COMPLETED` | Attempting to cancel completed payment |
| `CANNOT_REFUND_INCOMPLETE` | Attempting to refund non-completed payment |
| `REFUND_AMOUNT_EXCEEDS` | Refund amount exceeds original payment |

## Bank-Specific Information

### BCA Virtual Account
- **Prefix**: 70013
- **VA Format**: 70013 + customer code/transaction ID
- **Processing**: Real-time validation and confirmation

### Mandiri Virtual Account
- **Prefix**: 89008
- **VA Format**: 89008 + customer code/transaction ID
- **Processing**: Real-time validation and confirmation

### BNI Virtual Account
- **Prefix**: 8881
- **VA Format**: 8881 + customer code/transaction ID
- **Processing**: Real-time validation and confirmation

### BRI Virtual Account
- **Prefix**: 20107
- **VA Format**: 20107 + customer code/transaction ID
- **Processing**: Real-time validation and confirmation

### Permata Virtual Account
- **Prefix**: 90010
- **VA Format**: 90010 + customer code/transaction ID
- **Processing**: Real-time validation and confirmation

## Best Practices

1. **VA Number Display**: Clearly display VA number and bank name to customers
2. **Expiry Communication**: Inform customers of VA expiry time
3. **Amount Validation**: Always validate amounts before initiating payment
4. **Status Polling**: Implement automatic polling for pending payments
5. **Webhook Verification**: Always verify webhook signatures for security
6. **Idempotency**: Use unique transaction IDs to prevent duplicate payments
7. **Error Handling**: Log all errors and provide meaningful error messages
8. **Cleanup**: Regularly clean up expired payment records

## Security Considerations

1. **Webhook Security**: Verify webhook signatures using configured secret
2. **Transaction Validation**: Validate all transaction parameters before processing
3. **Amount Limits**: Enforce min/max amount limits to prevent fraud
4. **Audit Logging**: Log all payment operations for audit trail
5. **Data Protection**: Encrypt sensitive customer data at rest
6. **VA Number Security**: Generate unique VA numbers per transaction

## Troubleshooting

### Payment Not Completing

- Verify customer transferred to correct VA number
- Check gateway provider configuration
- Verify webhook endpoint is accessible
- Review gateway provider logs for errors

### VA Number Not Generated

- Check gateway provider supports VA for selected bank
- Verify bank is in supported banks list
- Check gateway provider credentials

### Amount Validation Errors

- Check configured min/max amounts in application.properties
- Verify amount is in IDR (Indonesian Rupiah)
- Minimum: 1,000 IDR
- Maximum: 50,000,000 IDR

## Integration Example

### E-commerce Checkout Flow

```java
// 1. Customer selects Bank Transfer at checkout
// 2. Create payment with selected bank
PaymentMethodContext context = new PaymentMethodContext();
context.setPaymentMethod(PaymentMethodType.VA_BCA);
context.setAmount(orderTotal);
// ... set other context properties

// 3. Initiate payment and get VA number
PaymentMethodResult result = bankTransferProcessor.initiate(context);

// 4. Display payment instructions
if (result.isSuccess()) {
    showPaymentInstructions(
        result.getVirtualAccountNumber(),
        result.getAmount(),
        result.getExpiryTime()
    );
}

// 5. Poll for payment status or wait for webhook
// 6. Update order status when payment completes
```

## License

Proprietary - Numou Platform

## Support

For support and questions, contact the Numou Platform development team.
