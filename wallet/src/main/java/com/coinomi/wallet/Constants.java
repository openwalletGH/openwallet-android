package com.coinomi.wallet;

import com.coinomi.stratumj.ServerAddress;
import com.coinomi.wallet.coins.Coin;
import com.coinomi.wallet.coins.CoinIcon;
import com.coinomi.wallet.coins.CoinIconSvg;
import com.coinomi.wallet.network.CoinAddress;
import com.google.bitcoin.core.NetworkParameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Giannis Dzegoutanis
 * @author Andreas Schildbach
 */
public class Constants {
    public static final boolean TEST = true;

    private static final String FILENAME_NETWORK_SUFFIX = TEST ? "" : "-test";

    public static final String WALLET_FILENAME_PROTOBUF = "wallet" + FILENAME_NETWORK_SUFFIX;

    public static final int PORT = 15001;
    public static final String HOST = "test.coinomi.com";


    public static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final Charset US_ASCII = Charset.forName("US-ASCII");

    // TODO implement mnemonic generation activity and use Wallet.generateMnemonic()
    public static final List<String> TEST_MNEMONIC = ImmutableList.of("citizen", "fever", "scale", "nurse", "brief", "round", "ski", "fiction", "car", "fitness", "pluck", "act");

    // TODO make dynamic
//    public static final List<CoinAddress> COINS_ADDRESSES = ImmutableList.of(
//            new CoinAddress(new Coin("Bitcoin", "BTC", false, new CoinIconSvg(R.raw.bitcoin)),
//                    new ServerAddress("test.coinomi.com",15001)),
//            new CoinAddress(new Coin("Litecoin", "LTC", false, new CoinIconSvg(R.raw.litecoin)),
//                    new ServerAddress("test.coinomi.com",15002)),
//            new CoinAddress(new Coin("Dogecoin", "DOGE", false, new CoinIconSvg(R.raw.dogecoin)),
//                    new ServerAddress("test.coinomi.com",15003)),
//            new CoinAddress(new Coin("Bitcoin", "BTC", true, new CoinIconSvg(R.raw.bitcoin_test)),
//                    new ServerAddress("test.coinomi.com",15001)),
//            new CoinAddress(new Coin("Litecoin", "LTC", true, new CoinIconSvg(R.raw.litecoin_test)),
//                    new ServerAddress("test.coinomi.com",15002)),
//            new CoinAddress(new Coin("Dogecoin", "DOGE", true, new CoinIconSvg(R.raw.dogecoin_test)),
//                    new ServerAddress("test.coinomi.com",15003)));

}
