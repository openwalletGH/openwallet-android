package com.coinomi.wallet.protos;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.Wallet;
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
