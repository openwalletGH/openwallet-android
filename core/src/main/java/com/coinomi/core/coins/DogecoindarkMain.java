package com.coinomi.core.coins;

import com.coinomi.core.coins.families.PeerFamily;

/**
 * @author John L. Jegutanis
 */
public class DogecoindarkMain extends PeerFamily {
    private DogecoindarkMain() {
        id = "verge.main";

        addressHeader = 30;
        p2shHeader = 33;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 158;

        name = "Dogecoindark (beta)";
        symbol = "DOGED";
        uriScheme = "dogecoindark";
        bip44Index = 77;
        unitExponent = 6;
        feeValue = value(100000);
        minNonDust = value(100000);
        softDustLimit = value(100000); // 0.1 DOGED
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static DogecoindarkMain instance = new DogecoindarkMain();
    public static synchronized DogecoindarkMain get() {
        return instance;
    }
}
