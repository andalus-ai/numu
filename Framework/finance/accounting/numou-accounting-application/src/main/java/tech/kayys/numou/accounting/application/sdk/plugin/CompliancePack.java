package tech.kayys.syirkah.accounting.application.sdk.plugin;

/**
 * Specialised PlatformPlugin that represents a country / standards compliance pack
 * (e.g. Indonesia PSAK, Malaysia MFRS, Saudi AAOIFI, US GAAP).
 *
 * <p>Compliance packs provide rules, report templates, disclosures and taxonomies
 * specific to a regulatory framework.
 */
public interface CompliancePack extends PlatformPlugin {

    /** IETF language tag of the primary jurisdiction, e.g. {@code "id-ID"}. */
    String jurisdiction();

    /** Short compliance-standard label, e.g. {@code "PSAK"}, {@code "AAOIFI"}. */
    String standardLabel();
}
