package tech.kayys.numou.education.pesantren.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WalletTransaction(Type type, BigDecimal amount, LocalDate date, String note) {
    public enum Type { DEPOSIT, SPEND }
}
