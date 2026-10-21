package tech.kayys.numou.mobility.wallet.application;

import tech.kayys.numou.mobility.wallet.domain.Wallet;

import java.math.BigDecimal;

public record WalletView(String currency, BigDecimal balance, BigDecimal available, BigDecimal held, boolean canBook, boolean canAcceptCash, boolean frozen) {

    public static WalletView of(Wallet w) {
        return new WalletView(w.currency(), w.balance(), w.available(), w.balance().subtract(w.available()), w.canBook(), w.canAcceptCash(), w.frozen());
    }
}
