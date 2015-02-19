package com.coinomi.wallet;

import android.text.format.DateUtils;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.BitcoinTest;
import com.coinomi.core.coins.BlackcoinMain;
import com.coinomi.core.coins.CannacoinMain;
import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DarkcoinMain;
import com.coinomi.core.coins.DigibyteMain;
import com.coinomi.core.coins.DigitalcoinMain;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.DogecoinTest;
import com.coinomi.core.coins.FeathercoinMain;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.coins.LitecoinTest;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.coins.NuSharesMain;
import com.coinomi.core.coins.PeercoinMain;
import com.coinomi.core.coins.ReddcoinMain;
import com.coinomi.core.coins.RubycoinMain;
import com.coinomi.core.coins.UroMain;
import com.coinomi.core.network.CoinAddress;
import com.coinomi.stratumj.ServerAddress;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.bitcoinj.utils.MonetaryFormat;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author John L. Jegutanis
 * @author Andreas Schildbach
 */
public class Constants {

    public static final int SEED_ENTROPY_DEFAULT = 160;
    public static final int SEED_ENTROPY_EXTRA = 256;

    public static final String ARG_SEED = "seed";
    public static final String ARG_SEED_PROTECT = "seed_protect";
    public static final String ARG_PASSWORD = "password";
    public static final String ARG_SEND_TO_ADDRESS = "send_to_address";
    public static final String ARG_SEND_AMOUNT = "send_amount";
    public static final String ARG_COIN_ID = "coin_id";
    public static final String ARG_MULTIPLE_COIN_IDS = "multiple_coin_ids";
    public static final String ARG_MULTIPLE_CHOICE = "multiple_choice";
    public static final String ARG_TRANSACTION_ID = "transaction_id";
    public static final String ARG_ERROR = "error";
    public static final String ARG_MESSAGE = "message";
    public static final String ARG_ADDRESS = "address";
    public static final String ARG_TEST_WALLET = "test_wallet";

    public static final String WALLET_FILENAME_PROTOBUF = "wallet";
    public static final long WALLET_WRITE_DELAY = 3;
    public static final TimeUnit WALLET_WRITE_DELAY_UNIT = TimeUnit.SECONDS;

    public static final long STOP_SERVICE_AFTER_IDLE_SECS = 30 * 60; // 30 mins

    public static final int HTTP_TIMEOUT_MS = 15 * (int) DateUtils.SECOND_IN_MILLIS;

    public static final long RATE_UPDATE_FREQ_MS = 1 * DateUtils.MINUTE_IN_MILLIS;

    /** Default currency to use if all default mechanisms fail. */
    public static final String DEFAULT_EXCHANGE_CURRENCY = "USD";
    public static final MonetaryFormat LOCAL_CURRENCY_FORMAT =
            new MonetaryFormat().noCode().minDecimals(2).optionalDecimals(2).postfixCode();

    public static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final Charset US_ASCII = Charset.forName("US-ASCII");

    public static final char CHAR_HAIR_SPACE = '\u200a';
    public static final char CHAR_THIN_SPACE = '\u2009';
    public static final char CHAR_ALMOST_EQUAL_TO = '\u2248';
    public static final char CHAR_CHECKMARK = '\u2713';
    public static final char CURRENCY_PLUS_SIGN = '+';
    public static final char CURRENCY_MINUS_SIGN = '-';

    // TODO move to resource files
    public static final List<CoinAddress> DEFAULT_COINS_SERVERS = ImmutableList.of(
            new CoinAddress(BitcoinMain.get(),      new ServerAddress("btc-cce-1.coinomi.net", 5001),
                                                    new ServerAddress("btc-cce-2.coinomi.net", 5001)),
            new CoinAddress(BitcoinTest.get(),      new ServerAddress("btc-cce-1.coinomi.net", 15001),
                                                    new ServerAddress("btc-cce-2.coinomi.net", 15001)),
            new CoinAddress(DogecoinMain.get(),     new ServerAddress("doge-cce-1.coinomi.net", 5003),
                                                    new ServerAddress("doge-cce-2.coinomi.net", 5003)),
            new CoinAddress(DogecoinTest.get(),     new ServerAddress("doge-cce-1.coinomi.net", 15003),
                                                    new ServerAddress("doge-cce-2.coinomi.net", 15003)),
            new CoinAddress(LitecoinMain.get(),     new ServerAddress("ltc-cce-1.coinomi.net", 5002),
                                                    new ServerAddress("ltc-cce-2.coinomi.net", 5002)),
            new CoinAddress(LitecoinTest.get(),     new ServerAddress("ltc-cce-1.coinomi.net", 15002),
                                                    new ServerAddress("ltc-cce-2.coinomi.net", 15002)),
            new CoinAddress(PeercoinMain.get(),     new ServerAddress("ppc-cce-1.coinomi.net", 5004),
                                                    new ServerAddress("ppc-cce-2.coinomi.net", 5004)),
            new CoinAddress(ReddcoinMain.get(),     new ServerAddress("rdd-cce-1.coinomi.net", 5014),
                                                    new ServerAddress("rdd-cce-2.coinomi.net", 5014)),
            new CoinAddress(NuSharesMain.get(),     new ServerAddress("nsr-cce-1.coinomi.net", 5011),
                                                    new ServerAddress("nsr-cce-2.coinomi.net", 5011)),
            new CoinAddress(NuBitsMain.get(),       new ServerAddress("nbt-cce-1.coinomi.net", 5012),
                                                    new ServerAddress("nbt-cce-2.coinomi.net", 5012)),
            new CoinAddress(BlackcoinMain.get(),    new ServerAddress("blk-cce-1.coinomi.net", 5015),
                                                    new ServerAddress("blk-cce-2.coinomi.net", 5015)),
            new CoinAddress(FeathercoinMain.get(),  new ServerAddress("ftc-cce-1.coinomi.net", 5017),
                                                    new ServerAddress("ftc-cce-2.coinomi.net", 5017)),
            new CoinAddress(DarkcoinMain.get(),     new ServerAddress("drk-cce-1.coinomi.net", 5013),
                                                    new ServerAddress("drk-cce-2.coinomi.net", 5013)),
            new CoinAddress(RubycoinMain.get(),     new ServerAddress("rby-cce-1.coinomi.net", 5018),
                                                    new ServerAddress("rby-cce-2.coinomi.net", 5018)),
            new CoinAddress(UroMain.get(),          new ServerAddress("uro-cce-1.coinomi.net", 5019),
                                                    new ServerAddress("uro-cce-2.coinomi.net", 5019)),
            new CoinAddress(DigitalcoinMain.get(),  new ServerAddress("dgc-cce-1.coinomi.net", 5020),
                                                    new ServerAddress("dgc-cce-2.coinomi.net", 5020)),
            new CoinAddress(CannacoinMain.get(),    new ServerAddress("ccn-cce-1.coinomi.net", 5021),
                                                    new ServerAddress("ccn-cce-2.coinomi.net", 5021)),
            new CoinAddress(DigibyteMain.get(),     new ServerAddress("digibytewiki.com", 50001),       // https://github.com/digibyte/electrum-dgb-server
                                                    new ServerAddress("digielectrum.cryptoservices.net", 50001)) // Not Yet Live
    );

    public static final HashMap<CoinType, Integer> COINS_ICONS;
    public static final HashMap<CoinType, String> COINS_BLOCK_EXPLORERS;
    static {
        COINS_ICONS = new HashMap<CoinType, Integer>();
        COINS_ICONS.put(CoinID.BITCOIN_MAIN.getCoinType(), R.drawable.bitcoin);
        COINS_ICONS.put(CoinID.BITCOIN_TEST.getCoinType(), R.drawable.bitcoin_test);
        COINS_ICONS.put(CoinID.DOGECOIN_MAIN.getCoinType(), R.drawable.dogecoin);
        COINS_ICONS.put(CoinID.DOGECOIN_TEST.getCoinType(), R.drawable.dogecoin_test);
        COINS_ICONS.put(CoinID.LITECOIN_MAIN.getCoinType(), R.drawable.litecoin);
        COINS_ICONS.put(CoinID.LITECOIN_TEST.getCoinType(), R.drawable.litecoin_test);
        COINS_ICONS.put(CoinID.PEERCOIN_MAIN.getCoinType(), R.drawable.peercoin);
        COINS_ICONS.put(CoinID.DARKCOIN_MAIN.getCoinType(), R.drawable.darkcoin);
        COINS_ICONS.put(CoinID.REDDCOIN_MAIN.getCoinType(), R.drawable.reddcoin);
        COINS_ICONS.put(CoinID.NUSHARES_MAIN.getCoinType(), R.drawable.nushares);
        COINS_ICONS.put(CoinID.NUBITS_MAIN.getCoinType(), R.drawable.nubits);
        COINS_ICONS.put(CoinID.BLACKCOIN_MAIN.getCoinType(), R.drawable.blackcoin);
        COINS_ICONS.put(CoinID.RUBYCOIN_MAIN.getCoinType(), R.drawable.rubycoin);
        COINS_ICONS.put(CoinID.NAMECOIN_MAIN.getCoinType(), R.drawable.namecoin);
        COINS_ICONS.put(CoinID.FEATHERCOIN_MAIN.getCoinType(), R.drawable.feathercoin);
        COINS_ICONS.put(CoinID.URO_MAIN.getCoinType(), R.drawable.uro);
        COINS_ICONS.put(CoinID.DIGITALCOIN_MAIN.getCoinType(), R.drawable.digitalcoin);
        COINS_ICONS.put(CoinID.CANNACOIN_MAIN.getCoinType(), R.drawable.cannacoin);
        COINS_ICONS.put(CoinID.DIGIBYTE_MAIN.getCoinType(), R.drawable.digibyte);        

        COINS_BLOCK_EXPLORERS = new HashMap<CoinType, String>();
        COINS_BLOCK_EXPLORERS.put(CoinID.BITCOIN_MAIN.getCoinType(), "https://blockchain.info/tx/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.BITCOIN_TEST.getCoinType(), "https://chain.so/tx/BTCTEST/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.DOGECOIN_MAIN.getCoinType(), "https://chain.so/tx/DOGE/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.DOGECOIN_TEST.getCoinType(), "https://chain.so/tx/DOGETEST/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.LITECOIN_MAIN.getCoinType(), "http://ltc.blockr.io/tx/info/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.LITECOIN_TEST.getCoinType(), "https://chain.so/tx/LTCTEST/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.PEERCOIN_MAIN.getCoinType(), "http://ppc.blockr.io/tx/info/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.DARKCOIN_MAIN.getCoinType(), "https://bitinfocharts.com/darkcoin/tx/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.NUSHARES_MAIN.getCoinType(), "http://blockexplorer.nu/transactions/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.NUBITS_MAIN.getCoinType(), "http://blockexplorer.nu/transactions/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.REDDCOIN_MAIN.getCoinType(), "http://live.reddcoin.com/tx/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.BLACKCOIN_MAIN.getCoinType(), "http://www.blackcha.in/tx/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.RUBYCOIN_MAIN.getCoinType(), "https://chainz.cryptoid.info/rby/tx.dws?%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.NAMECOIN_MAIN.getCoinType(), "https://explorer.namecoin.info/tx/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.FEATHERCOIN_MAIN.getCoinType(), "http://explorer.feathercoin.com/tx/%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.URO_MAIN.getCoinType(), "https://chainz.cryptoid.info/uro/tx.dws?%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.DIGITALCOIN_MAIN.getCoinType(), "https://chainz.cryptoid.info/dgc/tx.dws?%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.CANNACOIN_MAIN.getCoinType(), "https://chainz.cryptoid.info/ccn/tx.dws?%s");
        COINS_BLOCK_EXPLORERS.put(CoinID.DIGIBYTE_MAIN.getCoinType(), "https://digiexplorer.info/tx/%s");        
    }

    public static final CoinType DEFAULT_COIN = BitcoinMain.get();
    public static final List<CoinType> DEFAULT_COINS = ImmutableList.of((CoinType) BitcoinMain.get());
    public static final ArrayList<String> DEFAULT_TEST_COIN_IDS = Lists.newArrayList(
            BitcoinTest.get().getId(),
            LitecoinTest.get().getId(),
            DogecoinTest.get().getId()
    );

    public static final List<CoinType> SUPPORTED_COINS = ImmutableList.of(
            BitcoinMain.get(),
            DogecoinMain.get(),
            LitecoinMain.get(),
            NuBitsMain.get(),
            PeercoinMain.get(),
            NuSharesMain.get(),
            DarkcoinMain.get(),
            BlackcoinMain.get(),
            RubycoinMain.get(),
            ReddcoinMain.get(),
            FeathercoinMain.get(),
            DigitalcoinMain.get(),
            CannacoinMain.get(),
            BitcoinTest.get(),
            LitecoinTest.get(),
            DogecoinTest.get()
    );
}
