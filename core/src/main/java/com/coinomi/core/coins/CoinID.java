package com.coinomi.core.coins;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.Networks;

import com.coinomi.core.uri.CoinURIParseException;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Set;

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
    REDDCOIN_MAIN(ReddcoinMain.get()),
    PEERCOIN_MAIN(PeercoinMain.get()),
    PEERCOIN_TEST(PeercoinTest.get()),
    DARKCOIN_MAIN(DarkcoinMain.get()),
    DARKCOIN_TEST(DarkcoinTest.get()),
    NUSHARES_MAIN(NuSharesMain.get()),
    NUBITS_MAIN(NuBitsMain.get()),
    NAMECOIN_MAIN(NamecoinMain.get()),
    BLACKCOIN(BlackcoinMain.get())
    ;

    static {
        Set<NetworkParameters> bitcoinjNetworks = Networks.get();
        for (NetworkParameters network : bitcoinjNetworks) {
            Networks.unregister(network);
        }

        for (CoinID id : values()) {
            Networks.register(id.type);
        }
    }

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

    public static List<CoinType> getSupportedCoins() {
        ImmutableList.Builder<CoinType> builder = ImmutableList.builder();
        for (CoinID id : values()) {
            builder.add(id.type);
        }
        return builder.build();
    }

    public static CoinType typeFromId(String stringId) {
        return fromId(stringId).type;
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

    public static CoinType typeFromAddress(String address) throws AddressFormatException {
        NetworkParameters addressParams = new Address(null, address).getParameters();
        if (addressParams instanceof CoinType) {
            return (CoinType) addressParams;
        } else {
            throw new AddressFormatException("Unsupported address network: " + addressParams.getId());
        }
    }

    public static CoinType typeFromSymbol(String symbol) {
        for(CoinID id : values()) {
            if (id.type.getSymbol().equalsIgnoreCase(symbol)) return id.type;
        }
        throw new IllegalArgumentException();
    }
}