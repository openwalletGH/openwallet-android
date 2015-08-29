package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;
import com.coinomi.core.coins.families.PeerFamily;

import org.bitcoinj.core.Coin;

/**
 * @author John L. Jegutanis
 */
public class ParkbyteMain extends CoinType {
    private ParkbyteMain() {
        id = "parkbyte.main";

        addressHeader = 55;
        p2shHeader = 28;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 5;

        family = PeerFamily.get();
        name = "ParkByte";
        symbol = "PKB";
        uriScheme = "parkbyte";
        bip44Index = 36;
        unitExponent = 8;
        feePerKb = value(1000); // 0.0001PKB
        minNonDust = value(1000); // 0.01PKB
        softDustLimit = minNonDust;
        softDustPolicy = SoftDustPolicy.NO_POLICY;
        signedMessageHeader = toBytes("ParkByte Signed Message:\n");
    }

    private static ParkbyteMain instance = new ParkbyteMain();
    public static synchronized ParkbyteMain get() {
        return instance;
    }
}
