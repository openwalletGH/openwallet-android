package com.coinomi.core;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
public class Constants {
    // TODO implement mnemonic generation activity and use Wallet.generateMnemonic()
    public static final List<String> TEST_MNEMONIC = ImmutableList.of("citizen", "fever", "scale", "nurse", "brief", "round", "ski", "fiction", "car", "fitness", "pluck", "act");

    public static final String ID_BITCOIN_MAIN = "bitcoin.main";
    public static final String ID_BITCOIN_TEST = "bitcoin.test";
    public static final String ID_LITECOIN_MAIN = "litecoin.main";
    public static final String ID_LITECOIN_TEST = "litecoin.test";
    public static final String ID_PEERCOIN_MAIN = "peercoin.main";
    public static final String ID_PEERCOIN_TEST = "peercoin.test";
    public static final String ID_DOGECOIN_MAIN = "dogecoin.main";
    public static final String ID_DOGECOIN_TEST = "dogecoin.test";
    public static final String ID_DARKCOIN_MAIN = "darkcoin.main";
    public static final String ID_DARKCOIN_TEST = "darkcoin.test";
}
