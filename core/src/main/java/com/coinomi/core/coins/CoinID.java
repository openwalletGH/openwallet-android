package com.coinomi.core.coins;

/**
 * @author Giannis Dzegoutanis
 */
public enum CoinID {
    BITCOIN_MAIN(BitcoinMain.get()),
    BITCOIN_TEST(BitcoinTest.get()),
    LITECOIN_MAIN(LitecoinMain.get()),
    LITECOIN_TEST(LitecoinTest.get()),
    PEERCOIN_MAIN(PeercoinMain.get()),
    PEERCOIN_TEST(PeercoinTest.get()),
    DOGECOIN_MAIN(DogecoinMain.get()),
    DOGECOIN_TEST(DogecoinTest.get()),
    DARKCOIN_MAIN(DarkcoinMain.get()),
    DARKCOIN_TEST(DarkcoinTest.get());

    private final CoinType type;

    private CoinID(final CoinType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type.getId();
    }

    public CoinType getCoinType() {
        return type;
    }

    public static CoinID fromId(String stringId) {
        for(CoinID id : values()) {
            if (id.type.getId().equalsIgnoreCase(stringId)) return id;
        }
        throw new IllegalArgumentException();
    }

    public static CoinID fromUri(String input) {
        for(CoinID id : values()) {
            if (input.startsWith(id.getCoinType().getUriScheme()+"://")) {
                return id;
            } else if (input.startsWith(id.getCoinType().getUriScheme()+":")) {
                return id;
            }
        }
        throw new IllegalArgumentException();
    }
}