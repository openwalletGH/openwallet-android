package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.nxt.Account;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.wallet.AbstractAddress;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
final public class NxtFamilyAddress implements AbstractAddress {
    private final CoinType type;
    private final byte[] publicKey;
    private final long accountId;
    private final String rsAccount;

    public NxtFamilyAddress(CoinType type, byte[] pubKey) {
        this.type = type;
        publicKey = pubKey;
        accountId = Account.getId(pubKey);
        rsAccount = Convert.rsAccount(type, accountId);
    }

    public NxtFamilyAddress(CoinType type, long accountId, String rsAccount) {
        this.type = type;
        publicKey = null;
        this. accountId = accountId;
        this.rsAccount = rsAccount;
    }

    public CoinType getType() {
        return type;
    }

    @Nullable
    public byte[] getPublicKey() {
        return publicKey;
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
