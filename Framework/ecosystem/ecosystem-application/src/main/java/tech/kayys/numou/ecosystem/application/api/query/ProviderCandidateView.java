package tech.kayys.syirkah.ecosystem.application.api.query;

/**
 * A participant that can currently supply a capability.
 *
 * <p>This is the shape the business actually asks for. It never mentions
 * a concrete fleet, TMS or 3PL vendor: "give me everyone who can do
 * logistics.transportation in ID-JB right now". See base00.md §8.
 *
 * @param participantId     the provider participant
 * @param participantCode   stable ecosystem handle
 * @param participantName   display name
 * @param provisioningModel how the capability is delivered
 * @param coverage          declared service area / scope
 */
public record ProviderCandidateView(
        String participantId,
        String participantCode,
        String participantName,
        String provisioningModel,
        String coverage) {
}
