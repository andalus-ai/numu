package tech.kayys.numou.affiliate.spi;
import tech.kayys.numou.affiliate.domain.ConversionReference;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
/** Source adapter verifies the qualifying business lifecycle; redirects and client-supplied status are not proof. */
public interface QualifiedConversionSourcePort {
    CompletionStage<Optional<ConversionReference>> findQualifiedConversion(
            String tenantId, String sourceType, String sourceId, String conversionEventId);
}
