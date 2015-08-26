package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;
import com.coinomi.core.coins.families.PeerFamily;

/**
 * @author dasource
 */
public class ShadowCashMain extends CoinType {
    private ShadowCashMain() {
        id = "shadowcash.main";

        addressHeader = 63;
        p2shHeader = 125;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 500;
		
        family = PeerFamily.get();
        name = "ShadowCash";
        symbol = "SDC";
        uriScheme = "shadowcash";
        bip44Index = 35;
        unitExponent = 8;
        feePerKb = Coin.valueOf(10000); // 0.0001 SDC
        minNonDust = Coin.valueOf(1);
        softDustLimit = Coin.valueOf(1000000); // 0.01 SDC
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("ShadowCash Signed Message:\n");
    }

    private static ShadowCashMain instance = new ShadowCashMain();
    public static synchronized ShadowCashMain get() {
        return instance;
    }
}
