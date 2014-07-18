package com.coinomi.core;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
public class Constants {
    // TODO implement mnemonic generation activity and use Wallet.generateMnemonic()
    public static final List<String> TEST_MNEMONIC = ImmutableList.of("citizen", "fever", "scale", "nurse", "brief", "round", "ski", "fiction", "car", "fitness", "pluck", "act");

    public static final int LOOKAHEAD = 5;

    public static final String ID_BITCOIN_MAIN = "com.coinomi.bitcoin";
    public static final String ID_BITCOIN_TEST = "com.coinomi.bitcoin.test";
    public static final String ID_LITECOIN_MAIN = "com.coinomi.litecoin";
    public static final String ID_LITECOIN_TEST = "com.coinomi.litecoin.test";
    public static final String ID_PEERCOIN_MAIN = "com.coinomi.bitcoin";
    public static final String ID_PEERCOIN_TEST = "com.coinomi.bitcoin.test";
    public static final String ID_DOGECOIN_MAIN = "com.coinomi.dogecoin";
    public static final String ID_DOGECOIN_TEST = "com.coinomi.dogecoin.test";
    public static final String ID_DARKCOIN_MAIN = "com.coinomi.bitcoin";
    public static final String ID_DARKCOIN_TEST = "com.coinomi.bitcoin.test";
}
