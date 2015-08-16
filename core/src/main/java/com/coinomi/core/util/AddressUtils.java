package com.coinomi.core.util;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.families.bitcoin.BitAddress;

import org.bitcoinj.script.Script;

import static com.coinomi.core.Preconditions.checkArgument;

/**
 * @author John L. Jegutanis
 */
public class AddressUtils {
    public static boolean isP2SHAddress(AbstractAddress address) {
        checkArgument(address instanceof BitAddress, "This address cannot be a P2SH address");
        return ((BitAddress) address).isP2SHAddress();
    }

    public static byte[] getHash160(AbstractAddress address) {
        checkArgument(address instanceof BitAddress, "Cannot get hash160 from this address");
        return ((BitAddress) address).getHash160();
    }

    public static BitAddress fromScript(CoinType type, Script script) {
        return new BitAddress(script.getToAddress(type));
    }
}
