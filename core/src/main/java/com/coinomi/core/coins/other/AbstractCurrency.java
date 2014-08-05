package com.coinomi.core.coins.other;

/**
 * @author Giannis Dzegoutanis
 */
abstract public class AbstractCurrency {
    private String name;
    private String symbol;

    protected AbstractCurrency(String name, String symbol) {
        this.name = name;
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }
}
