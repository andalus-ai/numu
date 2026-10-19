package tech.kayys.sy.currency.core.dto;

public class CurrencyInfo {
    private String code;
    private String name;
    private String symbol;
    private int decimalPlaces;
    private boolean custom;
    private Integer isoNumericCode;
    private String isoDisplayName;
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public int getDecimalPlaces() { return decimalPlaces; }
    public void setDecimalPlaces(int decimalPlaces) { this.decimalPlaces = decimalPlaces; }

    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }

    public Integer getIsoNumericCode() { return isoNumericCode; }
    public void setIsoNumericCode(Integer isoNumericCode) { this.isoNumericCode = isoNumericCode; }

    public String getIsoDisplayName() { return isoDisplayName; }
    public void setIsoDisplayName(String isoDisplayName) { this.isoDisplayName = isoDisplayName; }
}