package tech.kayys.syirkah.accounting.domain.tax;

/** Tax flow direction (Receivable vs Payable). */
public enum TaxDirection {
    INPUT_TAX,   // e.g. Pajak Masukan (Receivable/Creditable)
    OUTPUT_TAX   // e.g. Pajak Keluaran (Payable/Collected)
}
