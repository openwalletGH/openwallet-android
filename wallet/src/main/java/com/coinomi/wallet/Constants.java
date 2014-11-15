package com.coinomi.wallet;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DarkcoinMain;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.coins.NuSharesMain;
import com.coinomi.core.coins.PeercoinMain;
import com.coinomi.core.network.CoinAddress;
import com.coinomi.stratumj.ServerAddress;
import com.google.common.collect.ImmutableList;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Giannis Dzegoutanis
 * @author Andreas Schildbach
 */
public class Constants {

    public static final String ARG_SEED = "seed";
    public static final String ARG_SEED_PROTECT = "seed_protect";
    public static final String ARG_PASSWORD = "password";
    public static final String ARG_SEND_TO_ADDRESS = "to_address";
    public static final String ARG_COIN = "coin";
    public static final String ARG_TRANSACTION = "transaction";
    public static final String ARG_SEND_REQUEST = "send_request";
    public static final String ARG_ERROR = "error";
    public static final String ARG_MESSAGE = "message";

    public static final String WALLET_FILENAME_PROTOBUF = "wallet";
    public static final long WALLET_WRITE_DELAY = 3;
    public static final TimeUnit WALLET_WRITE_DELAY_UNIT = TimeUnit.SECONDS;

    public static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final Charset US_ASCII = Charset.forName("US-ASCII");

    public static final char CHAR_HAIR_SPACE = '\u200a';
    public static final char CHAR_THIN_SPACE = '\u2009';
    public static final char CHAR_ALMOST_EQUAL_TO = '\u2248';
    public static final char CHAR_CHECKMARK = '\u2713';
    public static final String CURRENCY_PLUS_SIGN = "+" + CHAR_THIN_SPACE;
    public static final String CURRENCY_MINUS_SIGN = "-" + CHAR_THIN_SPACE;

    // TODO make dynamic
    public static final List<CoinAddress> COINS_ADDRESSES_TEST = ImmutableList.of(
            new CoinAddress(BitcoinMain.get(), new ServerAddress("btc-cce-1.coinomi.net", 5001), new ServerAddress("btc-cce-2.coinomi.net", 5001)),
            new CoinAddress(DogecoinMain.get(), new ServerAddress("doge-cce-1.coinomi.net", 5003), new ServerAddress("doge-cce-2.coinomi.net", 5003)),
            new CoinAddress(LitecoinMain.get(), new ServerAddress("ltc-cce-1.coinomi.net", 5002), new ServerAddress("ltc-cce-2.coinomi.net", 5002)),
            new CoinAddress(PeercoinMain.get(), new ServerAddress("54.176.197.116", 5004)),
            new CoinAddress(DarkcoinMain.get(), new ServerAddress("54.176.197.116", 5013))
    );

    public static final HashMap<CoinType, Integer> COINS_ICONS;
    static {
        COINS_ICONS = new HashMap<CoinType, Integer>();
        COINS_ICONS.put(CoinID.BITCOIN_MAIN.getCoinType(), R.drawable.bitcoin);
        COINS_ICONS.put(CoinID.DOGECOIN_MAIN.getCoinType(), R.drawable.dogecoin);
        COINS_ICONS.put(CoinID.LITECOIN_MAIN.getCoinType(), R.drawable.litecoin);
        COINS_ICONS.put(CoinID.PEERCOIN_MAIN.getCoinType(), R.drawable.peercoin);
        COINS_ICONS.put(CoinID.DARKCOIN_MAIN.getCoinType(), R.drawable.darkcoin);
        COINS_ICONS.put(CoinID.NUSHARES_MAIN.getCoinType(), R.drawable.nushares);
        COINS_ICONS.put(CoinID.NUBITS_MAIN.getCoinType(), R.drawable.nubits);
    }

    public static final CoinType DEFAULT_COIN = BitcoinMain.get();
    public static final List<CoinType> DEFAULT_COINS = ImmutableList.of(BitcoinMain.get(),
            DogecoinMain.get(), LitecoinMain.get());
    public static final List<CoinType> SUPPORTED_COINS = CoinID.getSupportedCoins();
}
