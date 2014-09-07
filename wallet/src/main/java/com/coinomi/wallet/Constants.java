package com.coinomi.wallet;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.network.CoinAddress;
import com.coinomi.stratumj.ServerAddress;
import com.google.common.collect.ImmutableList;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Giannis Dzegoutanis
 * @author Andreas Schildbach
 */
public class Constants {
    public static final boolean TEST = true;

    private static final String FILENAME_NETWORK_SUFFIX = TEST ? "" : "-test";

    public static final String WALLET_FILENAME_PROTOBUF = "wallet" + FILENAME_NETWORK_SUFFIX;

    public static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final Charset US_ASCII = Charset.forName("US-ASCII");

    // TODO make dynamic
    public static final List<CoinAddress> COINS_ADDRESSES = ImmutableList.of(
            new CoinAddress(BitcoinMain.get(), new ServerAddress("test.coinomi.com", 5001)),
            new CoinAddress(LitecoinMain.get(), new ServerAddress("test.coinomi.com", 5002)),
            new CoinAddress(DogecoinMain.get(), new ServerAddress("test.coinomi.com", 5003)));

//    public static final List<CoinAddress> COINS_ADDRESSES_TEST = ImmutableList.of(
//            new CoinAddress(BitcoinTest.get(), new ServerAddress("test.coinomi.com", 15001)),
//            new CoinAddress(LitecoinTest.get(), new ServerAddress("test.coinomi.com", 15002)),
//            new CoinAddress(DogecoinTest.get(), new ServerAddress("test.coinomi.com", 15003)));
//
//    public static final List<CoinType> COINS_TEST = ImmutableList.of( BitcoinTest.get(),
//            LitecoinTest.get(), DogecoinTest.get());

    public static final List<CoinAddress> COINS_ADDRESSES_TEST = ImmutableList.of(
            new CoinAddress(DogecoinMain.get(), new ServerAddress("test.coinomi.com", 5003)));

    public static final List<CoinType> COINS_TEST = ImmutableList.of((CoinType)DogecoinMain.get());

    public static final List<CoinType> DEFAULT_COINS = ImmutableList.of(BitcoinMain.get(),
            LitecoinMain.get(), DogecoinMain.get());

    public static final long WALLET_WRITE_DELAY = 5;
    public static final TimeUnit WALLET_WRITE_DELAY_UNIT = TimeUnit.SECONDS;
}
