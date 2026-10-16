package tech.kayys.sy.currency.core.resource;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.sy.currency.core.domain.Currency;
import tech.kayys.sy.currency.core.dto.CurrencyCreateDto;
import tech.kayys.sy.currency.core.dto.CurrencyDto;
import tech.kayys.sy.currency.core.service.CurrencyService;
import tech.kayys.sy.currency.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/currencies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CurrencyResource {

    private static final Logger log = LoggerFactory.getLogger(CurrencyResource.class);

    @Inject
    CurrencyService currencyService;

    @GET
    public List<CurrencyDto> list() {
        log.info("Listing all currencies");
        return currencyService.listAll().stream()
            .map(CurrencyDto::from)
            .toList();
    }

    @GET
    @Path("/{code}")
    public Response findByCode(@PathParam("code") String code) {
        log.info("Finding currency by code: {}", code);
        return currencyService.findByCode(code.toUpperCase())
            .map(c -> Response.ok(CurrencyDto.from(c)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response create(@Valid CurrencyCreateDto dto) {
        try {
            log.info("Creating new currency with code: {}", dto.code);
            Currency currency = new Currency();
            currency.code = dto.code.toUpperCase();
            currency.name = dto.name;
            currency.symbol = dto.symbol;
            currency.decimalPlaces = dto.decimalPlaces;
            currency.active = dto.active;

            Currency created = currencyService.create(currency);
            log.info("Currency created successfully with code: {}", created.code);
            return Response.status(Response.Status.CREATED)
                .entity(CurrencyDto.from(created))
                .build();
        } catch (BusinessException e) {
            log.warn("Failed to create currency: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    @PUT
    @Path("/{code}")
    @Transactional
    public Response update(@PathParam("code") String code, CurrencyCreateDto dto) {
        try {
            log.info("Updating currency with code: {}", code);
            Currency currency = new Currency();
            currency.name = dto.name;
            currency.symbol = dto.symbol;
            currency.decimalPlaces = dto.decimalPlaces;
            currency.active = dto.active;

            Currency updated = currencyService.update(code.toUpperCase(), currency);
            log.info("Currency updated successfully with code: {}", updated.code);
            return Response.ok(CurrencyDto.from(updated)).build();
        } catch (BusinessException e) {
            log.warn("Failed to update currency: {}", e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    @DELETE
    @Path("/{code}")
    @Transactional
    public Response deactivate(@PathParam("code") String code) {
        try {
            log.info("Deactivating currency with code: {}", code);
            currencyService.deactivate(code.toUpperCase());
            log.info("Currency deactivated successfully with code: {}", code);
            return Response.noContent().build();
        } catch (BusinessException e) {
            log.warn("Failed to deactivate currency: {}", e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
}
