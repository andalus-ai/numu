package tech.kayys.syirkah.ecosystem.application.api.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.ecosystem.application.port.CapabilityRepository;
import tech.kayys.syirkah.ecosystem.application.port.ParticipantRepository;
import tech.kayys.syirkah.ecosystem.application.port.ProviderCapabilityRepository;
import tech.kayys.syirkah.ecosystem.domain.model.Capability;
import tech.kayys.syirkah.ecosystem.domain.model.ProviderCapability;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves a capability code into the providers that can supply it right now.
 *
 * <p>Internal fleet, Numou fleet, 3PL, carrier, courier and external
 * logistics platform all come out of this single query as equivalent
 * candidates. Nothing downstream needs to know which is which - see
 * base00.md §7 (provider-neutral shipment) and §8 (provider execution port).
 */
public final class FindProviderCandidatesHandler
        implements QueryHandler<FindProviderCandidatesQuery, List<ProviderCandidateView>> {

    private final CapabilityRepository capabilities;
    private final ProviderCapabilityRepository providerCapabilities;
    private final ParticipantRepository participants;

    public FindProviderCandidatesHandler(
            CapabilityRepository capabilities,
            ProviderCapabilityRepository providerCapabilities,
            ParticipantRepository participants) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities cannot be null");
        this.providerCapabilities = Objects.requireNonNull(providerCapabilities,
                "providerCapabilities cannot be null");
        this.participants = Objects.requireNonNull(participants, "participants cannot be null");
    }

    @Override
    public Uni<List<ProviderCandidateView>> handle(FindProviderCandidatesQuery query) {
        return capabilities.findByCode(query.capabilityCode())
                .flatMap(optionalCapability -> optionalCapability
                        .map(capability -> providersFor(capability, query.coverageFilter()))
                        .orElseGet(() -> Uni.createFrom().item(List.<ProviderCandidateView>of())));
    }

    private Uni<List<ProviderCandidateView>> providersFor(
            Capability capability,
            String coverageFilter) {

        return providerCapabilities.findActiveByCapability(capability.getId())
                .flatMap(claims -> {
                    if (claims.isEmpty()) {
                        return Uni.createFrom().item(List.<ProviderCandidateView>of());
                    }

                    final List<Uni<ProviderCandidateView>> unresolved = claims.stream()
                            .map(claim -> resolveCandidate(claim, coverageFilter))
                            .toList();

                    return Uni.join().all(unresolved)
                            .andFailFast()
                            .map(results -> results.stream()
                                    .filter(Objects::nonNull)
                                    .toList());
                });
    }

    /**
     * One claim becomes a view only when its participant may transact and
     * the required coverage matches; otherwise {@code null} is dropped.
     */
    private Uni<ProviderCandidateView> resolveCandidate(
            ProviderCapability claim,
            String coverageFilter) {

        return participants.findById(claim.getParticipantId())
                .map(optionalParticipant -> optionalParticipant
                        .filter(candidate -> candidate.canTransact())
                        .filter(candidate -> matchesCoverage(claim.getCoverage(), coverageFilter))
                        .map(candidate -> new ProviderCandidateView(
                                candidate.getId().value().toString(),
                                candidate.getCode(),
                                candidate.getName(),
                                claim.getProvisioningModel(),
                                claim.getCoverage()))
                        .orElse(null));
    }

    private static boolean matchesCoverage(String declared, String required) {
        if (required == null || required.isBlank()) {
            return true;
        }
        if (declared == null) {
            return false;
        }
        final var declaredTrimmed = declared.trim();
        if (declaredTrimmed.isEmpty() || "any".equalsIgnoreCase(declaredTrimmed)) {
            return true;
        }
        final var requiredKey = required.trim().toLowerCase(Locale.ROOT);
        return declaredTrimmed.toLowerCase(Locale.ROOT).contains(requiredKey);
    }
}
