package tech.kayys.numou.numouapplication;

import tech.kayys.numou.numoudomain.SyirkahPostingPlan.AccountRole;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Binds abstract posting roles to chart-of-accounts codes of a tenant. */
public record SyirkahAccountMap(Map<AccountRole, String> codes) {

    public SyirkahAccountMap {
        Map<AccountRole, String> copy = new EnumMap<>(AccountRole.class);
        for (AccountRole r : AccountRole.values()) {
            String code = Objects.requireNonNull(codes.get(r), "missing account code for " + r);
            if (code.isBlank()) {
                throw new IllegalArgumentException("blank account code for " + r);
            }
            copy.put(r, code);
        }
        codes = Map.copyOf(copy);
    }

    public String codeOf(AccountRole role) {
        return codes.get(role);
    }
}
