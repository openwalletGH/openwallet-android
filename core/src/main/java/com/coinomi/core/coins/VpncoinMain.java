package com.coinomi.core.coins;

import com.coinomi.core.coins.families.VpncoinFamily;

/**
 * @author John L. Jegutanis
 */
public class VpncoinMain extends VpncoinFamily {
    private VpncoinMain() {
        id = "vpncoin.main";

        addressHeader = 71;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 199;

        name = "Vpncoin";
        symbol = "VPN";
        uriScheme = "vpncoin";
        bip44Index = 33;
        unitExponent = 8;
        feeValue = value(10000000); // 0.1VPN
        minNonDust = value(10000000); // 0.1VPN
        softDustLimit = value(10000000); // 0.1VPN
        softDustPolicy = SoftDustPolicy.NO_POLICY;
        signedMessageHeader = toBytes("VpnCoin Signed Message:\n");
    }

    private static VpncoinMain instance = new VpncoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
