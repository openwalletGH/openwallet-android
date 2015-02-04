package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author John L. Jegutanis
 */
public class NuSharesMain extends CoinType {
    private NuSharesMain() {
        id = "nushares.main";

        addressHeader = 63;
        p2shHeader = 64;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        tokenId = 0x53;

        name = "NuShares";
        symbol = "NSR";
        uriScheme = "nushares";
        bip44Index = 11;
        unitExponent = 4;
        feePerKb = Coin.valueOf(10000); // 1NSR, careful NuBits has 10000 units per coin
        minNonDust = Coin.valueOf(1);
        softDustLimit = Coin.valueOf(10000); // 1NSR, careful NuBits has 10000 units per coin
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
    }

    private static NuSharesMain instance = new NuSharesMain();
    public static synchronized NuSharesMain get() {
        return instance;
    }
}
