package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, append-only entry in a partner's account. Reversals are new
 * entries
 * with a negated amount; nothing is ever updated or deleted. Entries form a
 * hash chain.
 */
public record PartnerMovement(UUID id, long sequence, PartnerId partner, MovementType type, Money amount,
                CloseId closeId, String reference, Instant occurredAt,
                String previousHash, String hash) implements Serializable {

        public static PartnerMovement create(long sequence, PartnerId partner, MovementType type, Money amount,
                        CloseId closeId, String reference, Instant at, String previousHash) {
                UUID id = UUID.nameUUIDFromBytes((sequence + "|" + partner.value() + "|" + type + "|" + reference)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String canonical = canonical(sequence, partner, type, amount, closeId, reference, at, previousHash);
                return new PartnerMovement(id, sequence, partner, type, amount, closeId, reference, at, previousHash,
                                AuditHash.sha256(canonical));
        }

        static String canonical(long sequence, PartnerId partner, MovementType type, Money amount, CloseId closeId,
                        String reference, Instant at, String previousHash) {
                return sequence + "|" + partner.value() + "|" + type + "|"
                                + amount.amount().stripTrailingZeros().toPlainString()
                                + "|" + amount.currency().code() + "|" + (closeId == null ? "-" : closeId.value()) + "|"
                                + reference + "|" + at.toString() + "|" + previousHash;
        }

        /** Recomputes the hash for tamper checks. */
        public boolean hashIsValid() {
                return hash.equals(AuditHash.sha256(canonical(sequence, partner, type, amount, closeId, reference,
                                occurredAt, previousHash)));
        }
}
