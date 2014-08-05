package com.coinomi.core.coins.other;

/**
 * @author Giannis Dzegoutanis
 */
public class Usd extends AbstractCurrency{

    private Usd() {
        super("US Dollar", "USD");
    }

    private static Usd instance;
    public static synchronized Usd get() {
        if (instance == null) {
            instance = new Usd();
        }
        return instance;
    }
}
