# Ky Payment Core - Test Guide

## Test Structure

```
src/test/java/tech/kayys/payment/
├── PaymentResourceTest.java          # REST API integration tests
├── PaymentServiceTest.java           # Service layer integration tests
├── dto/
│   ├── CreatePaymentRequestTest.java
│   └── RefundRequestTest.java
├── gateway/
│   ├── GatewayProviderTest.java
│   ├── GatewayConfigTest.java
│   ├── GatewayRequestTest.java
│   └── GatewayResponseTest.java
├── method/
│   ├── PaymentMethodTypeTest.java
│   ├── PaymentMethodContextTest.java
│   ├── PaymentMethodResultTest.java
│   └── processor/
│       ├── BankTransferProcessorTest.java
│       ├── QRISProcessorTest.java
│       ├── EWalletProcessorTest.java
│       └── FintechProcessorTest.java
└── service/
    ├── PaymentGatewayRegistryTest.java
    ├── PaymentMethodRegistryTest.java
    └── PaymentProcessorFactoryTest.java
```

## Running Tests

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=PaymentMethodTypeTest
mvn test -Dtest=PaymentServiceTest
mvn test -Dtest=PaymentResourceTest
```

### Run Tests by Package
```bash
mvn test -Dtest="tech.kayys.payment.method.**"
mvn test -Dtest="tech.kayys.payment.gateway.**"
mvn test -Dtest="tech.kayys.payment.service.**"
```

### Run Tests with Coverage
```bash
mvn test jacoco:report
```

### Run Tests in IDE
- **IntelliJ IDEA**: Right-click on test file/folder → Run
- **Eclipse**: Right-click on test file/folder → Run As → JUnit Test

## Test Categories

### Unit Tests
Pure unit tests that don't require Quarkus runtime:
- `PaymentMethodTypeTest`
- `PaymentMethodContextTest`
- `PaymentMethodResultTest`
- `GatewayProviderTest`
- `GatewayConfigTest`
- `GatewayRequestTest`
- `GatewayResponseTest`
- `CreatePaymentRequestTest`
- `RefundRequestTest`

Run fast unit tests only:
```bash
mvn test -Dtest="*Test" -Dquarkus.test.profile.tags=unit
```

### Integration Tests
Tests that require Quarkus runtime (`@QuarkusTest`):
- `PaymentServiceTest`
- `PaymentResourceTest`
- `PaymentGatewayRegistryTest`
- `PaymentMethodRegistryTest`
- `PaymentProcessorFactoryTest`
- `BankTransferProcessorTest`
- `QRISProcessorTest`
- `EWalletProcessorTest`
- `FintechProcessorTest`

Run integration tests:
```bash
mvn test -Dtest="*IT"
```

## Test Configuration

Test configuration is located in `src/test/resources/application.properties`:

```properties
# H2 in-memory database for tests
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb

# Drop and create schema for each test run
quarkus.hibernate-orm.database.generation=drop-and-create

# Test gateway credentials
payment.gateway.midtrans.server-key=SB-Mid-server-TEST_KEY
payment.gateway.xendit.secret-key=xnd_development_TEST_KEY
```

## Test Coverage Report

Generate HTML coverage report:
```bash
mvn clean test jacoco:report
```

Open report in browser:
```bash
open target/site/jacoco/index.html
```

## Writing New Tests

### Unit Test Example
```java
package tech.kayys.payment.method;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PaymentMethodTypeTest {
    
    @Test
    void testQRIS() {
        PaymentMethodType method = PaymentMethodType.QRIS;
        assertTrue(method.isQRIS());
        assertEquals("qris", method.getCode());
    }
}
```

### Integration Test Example
```java
package tech.kayys.payment;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class PaymentServiceTest {
    
    @Inject
    PaymentService paymentService;
    
    @Test
    void testCreatePayment() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmount(new BigDecimal("100000"));
        request.setPaymentMethod("QRIS");
        
        Payment payment = paymentService.createPayment(request);
        
        assertNotNull(payment);
        assertNotNull(payment.id);
    }
}
```

### REST API Test Example
```java
package tech.kayys.payment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PaymentResourceTest {
    
    @Test
    void testGetPaymentMethods() {
        given()
            .when().get("/api/payments/methods")
            .then()
            .statusCode(200)
            .body("payment_methods", notNullValue());
    }
}
```

## Test Data

### Sample Payment Request
```json
{
  "external_order_id": "ORDER-12345",
  "payment_method": "QRIS",
  "gateway_provider": "MIDTRANS",
  "amount": 100000,
  "currency": "IDR",
  "customer_name": "John Doe",
  "customer_email": "john@example.com",
  "customer_phone": "08123456789",
  "callback_url": "https://example.com/callback",
  "return_url": "https://example.com/return",
  "metadata": {
    "order_type": "online"
  }
}
```

### Sample Refund Request
```json
{
  "payment_id": 123,
  "amount": 50000,
  "reason": "Product return",
  "notes": "Customer requested refund"
}
```

## Troubleshooting

### Tests Fail Due to Database Issues
Ensure H2 database is properly configured:
```bash
mvn clean test -Dquarkus.hibernate-orm.database.generation=drop-and-create
```

### Tests Fail Due to Gateway Configuration
Mock gateway responses or use test credentials:
```properties
payment.gateway.midtrans.environment=SANDBOX
```

### Port Already in Use
Change test port:
```properties
quarkus.http.test-port=8082
```

## CI/CD Integration

### GitHub Actions
```yaml
- name: Run Tests
  run: mvn test

- name: Upload Coverage
  uses: codecov/codecov-action@v3
  with:
    files: target/site/jacoco/jacoco.xml
```

### GitLab CI
```yaml
test:
  script:
    - mvn test
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml
    paths:
      - target/site/jacoco/
```

## Best Practices

1. **Test Isolation**: Each test should be independent
2. **Use Transactions**: Annotate tests with `@Transactional` for automatic rollback
3. **Clean Data**: Clean up test data in `@BeforeEach` or `@AfterEach`
4. **Mock External Services**: Use mocks for gateway calls in unit tests
5. **Descriptive Names**: Use `@DisplayName` for clear test descriptions
6. **Assert Early**: Fail fast with assertions at the beginning
7. **Test Edge Cases**: Test null values, empty collections, invalid inputs

## Coverage Goals

| Component | Target Coverage |
|-----------|----------------|
| Entities | 90% |
| DTOs | 95% |
| Processors | 85% |
| Services | 80% |
| REST API | 75% |
| **Overall** | **85%** |
