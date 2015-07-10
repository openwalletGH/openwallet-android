package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.CoreUtils;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.nxt.Account;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.coins.nxt.Crypto;
import com.coinomi.core.wallet.AbstractAddress;

import org.bitcoinj.crypto.DeterministicKey;

import static com.coinomi.core.CoreUtils.bytesToMnemonic;
import static com.coinomi.core.CoreUtils.getMnemonicToString;

/**
 * @author John L. Jegutanis
 */
public class NxtFamilyAddress implements AbstractAddress {
    private final long accountId;
    private final String rsAccount;

    public NxtFamilyAddress(CoinType type, DeterministicKey key) {
        String secret = getMnemonicToString(bytesToMnemonic(key.getPrivKeyBytes()));
        byte[] pubKey = Crypto.getPublicKey(secret);
        accountId = Account.getId(pubKey);
        rsAccount = Convert.rsAccount(type, accountId);
    }

    public long getAccountId() {
        return accountId;
    }

    public String getRsAccount() {
        return rsAccount;
    }

    @Override
    public String toString() {
        return getRsAccount();
    }
}
