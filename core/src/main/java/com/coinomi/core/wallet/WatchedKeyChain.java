package com.coinomi.core.wallet;

import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.KeyCrypter;

import javax.annotation.Nullable;

/**
 * @author Giannis Dzegoutanis
 */
public class WatchedKeyChain extends SimpleHDKeyChain {
    public WatchedKeyChain(DeterministicKey rootkey) {
        super(rootkey);
    }
}
