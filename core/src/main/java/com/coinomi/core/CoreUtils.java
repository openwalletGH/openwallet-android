package com.coinomi.core;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.families.BitFamily;
import com.coinomi.core.coins.families.CoinFamily;
import com.coinomi.core.coins.families.NxtFamily;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.SimpleHDKeyChain;
import com.coinomi.core.wallet.families.bitcoin.BitAddress;
import com.coinomi.core.wallet.families.nxt.NxtFamilyAddress;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;

import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;

import java.util.List;

/**
 * @author John L. Jegutanis
 */
public class CoreUtils {
    static public AbstractAddress parseAddress(String addressString) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Get the currently latest unused address by purpose.
     */
    @VisibleForTesting
    public static AbstractAddress currentAddress(CoinType type, SimpleHDKeyChain keys, SimpleHDKeyChain.KeyPurpose purpose) {
        DeterministicKey key = keys.getCurrentUnusedKey(purpose);
        CoinFamily family = type.getFamily();

        if (family instanceof BitFamily) {
            return new BitAddress(key.toAddress(type));
        } else if (family instanceof NxtFamily) {
            return new NxtFamilyAddress(type, key);
        } else {
            throw new RuntimeException("Unknown family: " + family.getClass().getName());
        }
    }

    public static String getMnemonicToString(List<String> mnemonic) {
        return Joiner.on(' ').join(mnemonic);
    }

//    public List<Address> getActiveAddresses() {
//        ImmutableList.Builder<Address> activeAddresses = ImmutableList.builder();
//        for (DeterministicKey key : keys.getActiveKeys()) {
//            activeAddresses.add(key.toAddress(coinType));
//        }
//        return activeAddresses.build();
//    }
//
//    public void markAddressAsUsed(Address address) {
//        keys.markPubHashAsUsed(address.getHash160());
//    }
//
    public static List<String> bytesToMnemonic(byte[] bytes) {
        List<String> mnemonic;
        try {
            mnemonic = MnemonicCode.INSTANCE.toMnemonic(bytes);
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException(e); // should not happen, we have 16bytes of entropy
        }
        return mnemonic;
    }

    public static String bytesToMnemonicString(byte[] bytes) {
        return getMnemonicToString(bytesToMnemonic(bytes));
    }
}
