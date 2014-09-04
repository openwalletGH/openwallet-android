package com.coinomi.core.coins.other;

/**
 * @author Giannis Dzegoutanis
 */
public class Cny extends AbstractCurrency{

    private Cny() {
        super("Yuan", "CNY");
    }

    private static Cny instance;
    public static synchronized Cny get() {
        if (instance == null) {
            instance = new Cny();
        }
        return instance;
    }
}
