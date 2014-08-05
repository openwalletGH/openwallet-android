package com.coinomi.core.coins.other;

/**
 * @author Giannis Dzegoutanis
 */
public class Eur extends AbstractCurrency{

    private Eur() {
        super("Euro", "EUR");
    }

    private static Eur instance;
    public static synchronized Eur get() {
        if (instance == null) {
            instance = new Eur();
        }
        return instance;
    }
}
