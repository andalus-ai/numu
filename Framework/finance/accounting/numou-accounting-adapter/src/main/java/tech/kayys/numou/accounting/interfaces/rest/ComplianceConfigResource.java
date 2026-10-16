package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.service.AccountingComplianceEngine;
import tech.kayys.syirkah.accounting.domain.compliance.ComplianceConfiguration;
import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;

@Path("/api/v1/accounting/compliance/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Compliance Configuration API", description = "Endpoints to view and dynamically toggle accounting compliance rules")
public class ComplianceConfigResource {

    @Inject
    AccountingComplianceEngine complianceEngine;

    
public record UpdateComplianceConfigRequest(
            String standard,
            boolean allowRibaAccounts,
            boolean enforceShariaContracts,
            boolean enableZakatAccounting,
            boolean enableIndonesianTaxWithholding,
            boolean strictPeriodLocking,
            boolean dualLedgerEnabled
    ) {}

    @GET
    @Operation(summary = "Get active accounting compliance configuration")
    public ComplianceConfiguration getConfiguration() {
        return complianceEngine.getConfiguration();
    }

    @PUT
    @Operation(summary = "Update accounting compliance standard and feature toggles")
    public Response updateConfiguration(UpdateComplianceConfigRequest req) {
        ComplianceStandard std = ComplianceStandard.valueOf(req.standard().toUpperCase());
        ComplianceConfiguration newConfig = new ComplianceConfiguration(
                std,
                req.allowRibaAccounts(),
                req.enforceShariaContracts(),
                req.enableZakatAccounting(),
                req.enableIndonesianTaxWithholding(),
                req.strictPeriodLocking(),
                req.dualLedgerEnabled()
        );
        complianceEngine.updateConfiguration(newConfig);
        return Response.ok(newConfig).build();
    }
}
