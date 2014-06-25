package com.coinomi.wallet.coins;


/**
 * @author Giannis Dzegoutanis
 */
final public class Coin {
    final private String name;
    final private String symbol;
    final private boolean isTest;
    final private CoinIcon icon;

    public Coin(String name, String symbol, boolean isTest, CoinIcon icon) {
        this.name = name;
        this.symbol = symbol;
        this.isTest = isTest;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isTest() {
        return isTest;
    }

    public CoinIcon getIcon() {
        return icon;
    }

    @Override
    public String toString() {
        return "Coin{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", isTest=" + isTest +
                ", icon=" + icon +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Coin coin = (Coin) o;

        if (isTest != coin.isTest) return false;
        if (!icon.equals(coin.icon)) return false;
        if (!name.equals(coin.name)) return false;
        if (!symbol.equals(coin.symbol)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + symbol.hashCode();
        result = 31 * result + (isTest ? 1 : 0);
        result = 31 * result + icon.hashCode();
        return result;
    }
}
