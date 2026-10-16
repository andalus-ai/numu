package tech.kayys.numou.accounting.domain.islamic;

/**
 * Partnership structure governing the allocation policy.
 *
 * <p>Types whose legal/accounting policy has not been explicitly implemented must not
 * silently fall back to another type's loss allocation rules.</p>
 */
public enum SyirkahType {
    MUDHARABAH,
    MUSYARAKAH,
    INAN,
    ABDAN,
    WUJUH
}
