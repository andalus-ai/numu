package tech.kayys.numou.identity.adapter.inbound.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantSelectionCredentialFilterTest {

    @Test
    void allowsOnlyTenantDiscoveryAndExchangeMethods() {
        assertTrue(TenantSelectionCredentialFilter.isAllowedSelectionRequest(
                "GET", "/api/v2/auth/tenants"));
        assertTrue(TenantSelectionCredentialFilter.isAllowedSelectionRequest(
                "POST", "api/v2/auth/tenant-exchange"));
    }

    @Test
    void deniesOtherRoutesMethodsAndMalformedPaths() {
        assertFalse(TenantSelectionCredentialFilter.isAllowedSelectionRequest(
                "GET", "/api/v1/users"));
        assertFalse(TenantSelectionCredentialFilter.isAllowedSelectionRequest(
                "POST", "/api/v2/auth/tenants"));
        assertFalse(TenantSelectionCredentialFilter.isAllowedSelectionRequest(
                "GET", "/api/v2/auth/tenant-exchange"));
        assertFalse(TenantSelectionCredentialFilter.isAllowedSelectionRequest(
                "GET", "/api/v2/auth/tenants/"));
        assertFalse(TenantSelectionCredentialFilter.isAllowedSelectionRequest(null, "/api/v2/auth/tenants"));
        assertFalse(TenantSelectionCredentialFilter.isAllowedSelectionRequest("GET", null));
    }
}
