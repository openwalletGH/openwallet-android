package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author John L. Jegutanis
 */
public class NamecoinMain extends CoinType {
    private NamecoinMain() {
        id = "namecoin.main";

        addressHeader = 52;
        // Namecoin does not have p2sh addresses
        acceptableAddressCodes = new int[] { addressHeader};
        spendableCoinbaseDepth = 100;

        name = "Namecoin (beta)";
        symbol = "NMC";
        uriScheme = "namecoin";
        bip44Index = 7;
        unitExponent = 8;
        feePerKb = Coin.valueOf(500000);
        minNonDust = Coin.valueOf(10000); // 0.0001 NMC mininput
        softDustLimit = Coin.valueOf(1000000); // 0.01 NMC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static NamecoinMain instance = new NamecoinMain();
    public static synchronized NamecoinMain get() {
        return instance;
    }
}
