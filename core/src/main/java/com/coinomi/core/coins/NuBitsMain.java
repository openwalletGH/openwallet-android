package com.coinomi.core.coins;

import com.coinomi.core.coins.families.NubitsFamily;

/**
 * @author John L. Jegutanis
 */
public class NuBitsMain extends CoinType {
    private NuBitsMain() {
        id = "nubits.main";

        addressHeader = 25;
        p2shHeader = 26;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        tokenId = 0x42;

        family = NubitsFamily.get();
        name = "NuBits";
        symbol = "NBT";
        uriScheme = "nu";
        bip44Index = 12;
        unitExponent = 4;
        feePerKb = value(200); // 0.02NBT, careful NuBits has 10000 units per coin
        minNonDust = value(100); // 0.01NBT
        softDustLimit = minNonDust;
        softDustPolicy = SoftDustPolicy.NO_POLICY;
        signedMessageHeader = toBytes("Nu Signed Message:\n");
    }

    private static NuBitsMain instance = new NuBitsMain();
    public static synchronized NuBitsMain get() {
        return instance;
    }
}
