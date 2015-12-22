package com.coinomi.core.coins;

import com.coinomi.core.coins.families.ReddFamily;

/**
 * @author L33tConsultant
 */
public class PotcoinMain extends ReddFamily {

    private PotcoinMain() {
        id = "potcoin.main";

        addressHeader = 55;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        transactionVersion = 4;
        dumpedPrivateKeyHeader = 189;

        name = "Potcoin (beta)";
        symbol = "POT";
        uriScheme = "potcoin";
        bip44Index = 81;
        unitExponent = 8;
        feeValue = value(100000);
        minNonDust = value(1000);
        softDustLimit = value(100000);
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static PotcoinMain instance = new PotcoinMain();
    public static synchronized PotcoinMain get() {
        return instance;
    }

}
