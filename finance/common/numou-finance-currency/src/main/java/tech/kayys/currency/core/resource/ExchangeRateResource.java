package tech.kayys.sy.currency.core.resource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.sy.currency.core.domain.ExchangeRate;
import tech.kayys.sy.currency.core.dto.CurrencyConversionRequest;
import tech.kayys.sy.currency.core.dto.ExchangeRateDto;
import tech.kayys.sy.currency.core.service.ExchangeRateService;
import tech.kayys.sy.currency.exception.BusinessException;

@Path("/exchange-rates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExchangeRateResource {

    @Inject
    ExchangeRateService exchangeRateService;

    @POST
    @Transactional
    public Response createExchangeRate(ExchangeRateDto dto) {
        try {
            ExchangeRate rate = exchangeRateService.createExchangeRate(dto);
            return Response.status(Response.Status.CREATED)
                .entity(ExchangeRateDto.from(rate))
                .build();
        } catch (BusinessException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/latest")
    public List<ExchangeRateDto> getLatestRates() {
        return exchangeRateService.getLatestRates().stream()
            .map(ExchangeRateDto::from)
            .toList();
    }

    @POST
    @Path("/convert")
    public Response convertCurrency(CurrencyConversionRequest request) {
        return exchangeRateService.convertCurrency(request)
            .map(result -> Response.ok(result).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Exchange rate not available for the specified currencies and date"))
                .build());
    }

    @GET
    @Path("/{fromCurrency}/{toCurrency}")
    public Response getExchangeRate(
            @PathParam("fromCurrency") String fromCurrency,
            @PathParam("toCurrency") String toCurrency,
            @QueryParam("date") LocalDate date) {
        
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        
        return exchangeRateService.getExchangeRate(fromCurrency, toCurrency, effectiveDate)
            .map(rate -> Response.ok(Map.of(
                "fromCurrency", fromCurrency,
                "toCurrency", toCurrency,
                "rate", rate,
                "date", effectiveDate
            )).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}