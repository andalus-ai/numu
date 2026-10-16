package tech.kayys.numou.crm.domain.account;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.valueobject.AccountStatus;
import tech.kayys.numou.crm.domain.valueobject.PartyRole;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {

    @Test
    void accountReferencesCanonicalParticipantIdentityAndNormalizesItsName() {
        ParticipantId participantId = ParticipantId.generate();
        Account account = Account.create(AccountId.generate(), participantId, "  Acme  ");

        assertEquals(participantId, account.participantId());
        assertEquals("Acme", account.name());
        assertEquals(AccountStatus.ACTIVE, account.status());
    }

    @Test
    void accountRejectsSelfAsParent() {
        AccountId accountId = AccountId.generate();
        Account account = Account.create(accountId, ParticipantId.generate(), "Acme");

        assertThrows(IllegalArgumentException.class, () -> account.assignParent(accountId));
        assertThrows(IllegalArgumentException.class,
                () -> Account.restore(accountId, ParticipantId.generate(), "Acme",
                        AccountStatus.ACTIVE, accountId, Set.of()));
    }

    @Test
    void accountRequiresNonBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> Account.create(AccountId.generate(), ParticipantId.of(UUID.randomUUID()), "  "));
    }
}
