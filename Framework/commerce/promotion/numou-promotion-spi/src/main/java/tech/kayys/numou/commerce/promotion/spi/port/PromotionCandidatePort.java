package tech.kayys.syirkah.commerce.promotion.spi.port;

import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionCandidate;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionResolveRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Candidate discovery port (product04.md section 5).
 *
 * <p>The engine re-validates status and validity window, so an over-fetching
 * implementation is safe; under-fetching changes outcomes. The port returns
 * versioned {@link PromotionCandidate} ids, NOT full aggregates -- bulk loading
 * the definitions is a separate step behind the {@link PromotionRepository}
 * port, so discovery stays storage-agnostic (product04.md section 28).
 *
 * <p>Two signatures are kept for migration: {@link #findActiveOn(LocalDate)}
 * supports the legacy v1 cart model (which does not carry tenant/channel/
 * branch/product references), while
 * {@link #findCandidates(PromotionResolveRequest)} is the typed, resolver-
 * driven path recommended for new callers.</p>
 */
public interface PromotionCandidatePort {

    CompletionStage<List<Promotion>> findActiveOn(LocalDate date);

    CompletionStage<List<PromotionCandidate>> findCandidates(PromotionResolveRequest request);
}
