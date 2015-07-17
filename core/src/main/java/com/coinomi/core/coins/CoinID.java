package com.coinomi.core.coins;

import com.coinomi.core.util.GenericUtils;
import com.google.common.collect.ImmutableList;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.Networks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * @author John L. Jegutanis
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
    DASH_MAIN(DashMain.get()),
    NUSHARES_MAIN(NuSharesMain.get()),
    NUBITS_MAIN(NuBitsMain.get()),
    NAMECOIN_MAIN(NamecoinMain.get()),
    BLACKCOIN_MAIN(BlackcoinMain.get()),
    MONACOIN_MAIN(MonacoinMain.get()),
    FEATHERCOIN_MAIN(FeathercoinMain.get()),
    RUBYCOIN_MAIN(RubycoinMain.get()),
//    URO_MAIN(UroMain.get()),
    DIGITALCOIN_MAIN(DigitalcoinMain.get()),
    CANNACOIN_MAIN(CannacoinMain.get()),
    DIGIBYTE_MAIN(DigibyteMain.get()),
    NEOSCOIN_MAIN(NeoscoinMain.get()),
    VERTCOIN_MAIN(VertcoinMain.get()),
    NXT_MAIN(NxtMain.get()),
    JUMBUCKS_MAIN(JumbucksMain.get()),
    ;

    private static List<CoinType> types;
    private static HashMap<String, CoinType> idLookup = new HashMap<>();
    private static HashMap<String, CoinType> symbolLookup = new HashMap<>();
    private static HashMap<String, List<CoinType>> uriLookup = new HashMap<>();

    static {
        Set<NetworkParameters> bitcoinjNetworks = Networks.get();
        for (NetworkParameters network : bitcoinjNetworks) {
            Networks.unregister(network);
        }

        for (CoinID id : values()) {
            Networks.register(id.type);
        }

        ImmutableList.Builder<CoinType> coinTypeBuilder = ImmutableList.builder();
        for (CoinID id : values()) {
            if (symbolLookup.containsKey(id.type.symbol)) {
                throw new IllegalStateException(
                        "Coin currency codes must be unique, double found: " + id.type.symbol);
            }
            symbolLookup.put(id.type.symbol, id.type);

            if (idLookup.containsKey(id.type.getId())) {
                throw new IllegalStateException(
                        "Coin IDs must be unique, double found: " + id.type.getId());
            }
            idLookup.put(id.type.getId(), id.type);

            if (!uriLookup.containsKey(id.type.uriScheme)) {
                uriLookup.put(id.type.uriScheme, new ArrayList<CoinType>());
            }
            uriLookup.get(id.type.uriScheme).add(id.type);

            coinTypeBuilder.add(id.type);
        }
        types = coinTypeBuilder.build();
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
        return types;
    }

    public static CoinType typeFromId(String stringId) {
        if (idLookup.containsKey(stringId)) {
            return idLookup.get(stringId);
        } else {
            throw new IllegalArgumentException("Unsupported ID: " + stringId);
        }
    }

    public static List<CoinType> fromUri(String input) {
        for (String uri : uriLookup.keySet()) {
            if (input.startsWith(uri + "://") || input.startsWith(uri + ":")) {
                return uriLookup.get(uri);
            }
        }
        throw new IllegalArgumentException("Unsupported URI: " + input);
    }

    public static List<CoinType> typesFromAddress(String address) throws AddressFormatException {
        return GenericUtils.getPossibleTypes(address);
    }

    public static boolean isSymbolSupported(String symbol) {
        return symbolLookup.containsKey(symbol);
    }

    public static CoinType typeFromSymbol(String symbol) {
        if (symbolLookup.containsKey(symbol.toUpperCase())) {
            return symbolLookup.get(symbol.toUpperCase());
        } else {
            throw new IllegalArgumentException("Unsupported coin symbol: " + symbol);
        }
    }
}
