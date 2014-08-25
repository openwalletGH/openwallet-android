package com.coinomi.core.crypto;

import com.google.bitcoin.crypto.KeyCrypterException;
import com.google.bitcoin.crypto.KeyCrypterScrypt;

import org.spongycastle.crypto.params.KeyParameter;

/**
 * @author Giannis Dzegoutanis
 */
public class KeyCrypterPin extends KeyCrypterScrypt {

    /**
     * Connect to a PIN server to retrieve the encryption key
     *
     * @param pin         The PIN number that corresponds to the account
     * @return            The KeyParameter containing the created AES key
     * @throws            KeyCrypterException
     */
    @Override
    public KeyParameter deriveKey(CharSequence pin) throws KeyCrypterException {
        throw new KeyCrypterException("method not implemented yet");
    }
}
