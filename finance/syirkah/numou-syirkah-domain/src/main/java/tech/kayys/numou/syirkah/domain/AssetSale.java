package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.util.Objects;

/**
 * One asset converted to cash during liquidation. Hak syuf'ah is checked per asset: a sale to an outsider is only
 * valid if the partners were first offered that asset at the same price; a sale to a partner is that offer taken up.
 *
 * @param description  what was sold (free text, audit trail)
 * @param proceeds     cash received for it
 * @param buyer        PARTNER or OUTSIDER
 * @param buyerPartner the buying partner (required for PARTNER sales, absent for OUTSIDER sales)
 * @param shufahOffered partners were offered this asset before it went to an outsider (ignored for PARTNER sales)
 */
public record AssetSale(String description, Money proceeds, Buyer buyer, PartnerId buyerPartner,
                        boolean shufahOffered) implements Serializable {

    public enum Buyer { PARTNER, OUTSIDER }

    public AssetSale {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("an asset sale needs a description");
        }
        Objects.requireNonNull(proceeds, "proceeds");
        Objects.requireNonNull(buyer, "buyer");
        if (proceeds.isNegative()) {
            throw new IllegalArgumentException("sale proceeds must be non-negative");
        }
        if (buyer == Buyer.PARTNER && buyerPartner == null) {
            throw new IllegalArgumentException("a sale to a partner must name the partner");
        }
        if (buyer == Buyer.OUTSIDER && buyerPartner != null) {
            throw new IllegalArgumentException("a sale to an outsider cannot name a partner");
        }
    }

    public static AssetSale toPartner(String description, Money proceeds, PartnerId partner) {
        return new AssetSale(description, proceeds, Buyer.PARTNER, partner, true);
    }

    public static AssetSale toOutsider(String description, Money proceeds, boolean shufahOffered) {
        return new AssetSale(description, proceeds, Buyer.OUTSIDER, null, shufahOffered);
    }

    /** True when this sale respects hak syuf'ah. */
    public boolean respectsShufah() {
        return buyer == Buyer.PARTNER || shufahOffered;
    }

    String canonical() {
        return description + "~" + proceeds.currency().code() + ":"
                + proceeds.amount().stripTrailingZeros().toPlainString() + "~" + buyer + "~"
                + (buyerPartner == null ? "-" : buyerPartner.value().toString()) + "~" + shufahOffered;
    }
}
