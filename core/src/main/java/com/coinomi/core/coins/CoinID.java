package com.coinomi.core.coins;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
public enum CoinID {
    BITCOIN_MAIN(BitcoinMain.get()),
    BITCOIN_TEST(BitcoinTest.get()),
    LITECOIN_MAIN(LitecoinMain.get()),
    LITECOIN_TEST(LitecoinTest.get()),
    DOGECOIN_MAIN(DogecoinMain.get()),
    DOGECOIN_TEST(DogecoinTest.get()),
    PEERCOIN_MAIN(PeercoinMain.get()),
    PEERCOIN_TEST(PeercoinTest.get()),
    DARKCOIN_MAIN(DarkcoinMain.get()),
    DARKCOIN_TEST(DarkcoinTest.get()),
    NUSHARES_MAIN(NuSharesMain.get()),
    NUBITS_MAIN(NuBitsMain.get())
    ;

    private final CoinType type;

    private CoinID(final CoinType type) {
        if (type.getUid() < 0) throw new RuntimeException("No UID defined for " + type.getName());
        this.type = type;
    }

    @Override
    public String toString() {
        return type.getId();
    }

    public CoinType getCoinType() {
        return type;
    }

    public static List<CoinType> getSupportedCoins() {
        ImmutableList.Builder<CoinType> builder = ImmutableList.builder();
        for (CoinID id : values()) {
            builder.add(id.type);
        }
        return builder.build();
    }

    public static CoinID fromId(String stringId) {
        for(CoinID id : values()) {
            if (id.type.getId().equalsIgnoreCase(stringId)) return id;
        }
        throw new IllegalArgumentException();
    }

    public static CoinID fromUid(long uid) {
        for(CoinID id : values()) {
            if (id.type.getUid() == uid) return id;
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