package com.coinomi.core.protos;

import com.coinomi.core.Constants;
import com.coinomi.core.Wallet;
import com.google.bitcoin.store.UnreadableWalletException;

import java.io.InputStream;

/**
 * @author Giannis Dzegoutanis
 */
public class WalletProtobufSerializer {

    public Wallet readWallet(InputStream input) throws UnreadableWalletException {
        //TODO
        try {
            return new Wallet(Constants.TEST_MNEMONIC);
        } catch (Exception e) {
            throw new UnreadableWalletException(e.getMessage());
        }
    }
}
