package tech.kayys.bill.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.bill.dto.BillSplitDTO;
import tech.kayys.bill.dto.SplitBillRequest;
import tech.kayys.bill.service.BillSplitService;

import java.util.List;

@Path("/api/bill-splits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BillSplitResource {
    
    @Inject
    BillSplitService billSplitService;
    
    @POST
    public Response splitBill(@Valid SplitBillRequest request) {
        List<BillSplitDTO> splits = billSplitService.splitBill(request);
        return Response.status(Response.Status.CREATED).entity(splits).build();
    }
    
    @GET
    @Path("/transaction/{transactionId}")
    public List<BillSplitDTO> getBillSplits(@PathParam("transactionId") Long transactionId) {
        return billSplitService.getBillSplits(transactionId);
    }
}