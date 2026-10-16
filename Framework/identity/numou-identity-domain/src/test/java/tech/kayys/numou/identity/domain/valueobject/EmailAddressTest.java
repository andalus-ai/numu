package tech.kayys.syirkah.identity.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailAddressTest {

    @Test
    void normalizesCaseAndWhitespace() {
        var email = EmailAddress.of("  Jane.Doe@Example.COM ");

        assertEquals("jane.doe@example.com", email.value());
    }

    @Test
    void rejectsMissingAtSign() {
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("jane.example.com"));
    }

    @Test
    void rejectsMissingDomain() {
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("jane@"));
    }

}
