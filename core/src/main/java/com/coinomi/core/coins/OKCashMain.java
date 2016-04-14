package com.coinomi.core.coins;

import com.coinomi.core.coins.families.PeerFamily;

/**
 * @author OKtoshi
 */
public class OKCashMain extends PeerFamily {
    private OKCashMain() {
        id = "okcash.main";

        addressHeader = 55;
        p2shHeader = 28;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 500;

        name = "OKCash (beta)";
        symbol = "OK";
        uriScheme = "okcash";
        bip44Index = 69;
        unitExponent = 8;
        feeValue = value(10000); // 0.0001 OK
        minNonDust = value(1);
        softDustLimit = value(1000000); // 0.01 OK
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("OKCash Signed Message:\n");
    }

    private static OKCashMain instance = new OKCashMain();
    public static synchronized OKCashMain get() {
        return instance;
    }
}