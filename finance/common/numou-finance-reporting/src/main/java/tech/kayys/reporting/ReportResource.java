package tech.kayys.reporting;

import java.time.LocalDate;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportResource {
    @Inject
    ReportingService reportingService;
    
    @GET
    @Path("/revenue")
    public RevenueReport getRevenueReport(
            @QueryParam("startDate") String startDateStr,
            @QueryParam("endDate") String endDateStr) {
        
        LocalDate startDate = startDateStr != null ? 
            LocalDate.parse(startDateStr) : LocalDate.now().withDayOfMonth(1);
        
        LocalDate endDate = endDateStr != null ?
            LocalDate.parse(endDateStr) : LocalDate.now();
        
        return reportingService.generateRevenueReport(startDate, endDate);
    }
    
    @GET
    @Path("/accounts-receivable")
    public AccountsReceivableReport getAccountsReceivableReport() {
        return reportingService.generateAccountsReceivableReport();
    }
    
    @GET
    @Path("/customer-analysis")
    public CustomerAnalysisReport getCustomerAnalysisReport(
            @QueryParam("startDate") String startDateStr,
            @QueryParam("endDate") String endDateStr) {
        
        LocalDate startDate = startDateStr != null ? 
            LocalDate.parse(startDateStr) : LocalDate.now().minusMonths(6).withDayOfMonth(1);
        
        LocalDate endDate = endDateStr != null ?
            LocalDate.parse(endDateStr) : LocalDate.now();
        
        return reportingService.generateCustomerAnalysisReport(startDate, endDate);
    }
}
