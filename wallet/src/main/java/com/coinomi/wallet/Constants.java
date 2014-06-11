package com.coinomi.wallet;

import com.google.bitcoin.core.NetworkParameters;

/**
 * @author Giannis Dzegoutanis
 * @author Andreas Schildbach
 */
public class Constants {
    public static final boolean TEST = true;

    private static final String FILENAME_NETWORK_SUFFIX = TEST ? "" : "-test";

    public static final String WALLET_FILENAME_PROTOBUF = "wallet" + FILENAME_NETWORK_SUFFIX;


}
