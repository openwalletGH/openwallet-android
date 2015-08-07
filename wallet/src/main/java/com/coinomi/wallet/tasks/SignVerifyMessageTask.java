package com.coinomi.wallet.tasks;

import android.os.AsyncTask;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.SignedMessage;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;

import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public abstract class SignVerifyMessageTask extends AsyncTask<SignedMessage, Void, SignedMessage> {
    private final WalletAccount account;
    private final boolean signMessage;
    @Nullable private final String password;

    public SignVerifyMessageTask(WalletAccount account, boolean signMessage, @Nullable String password) {
        this.account = account;
        this.signMessage = signMessage;
        this.password = password;
    }

    @Override
    protected SignedMessage doInBackground(SignedMessage... params) {
        SignedMessage message = params[0];

        KeyParameter key = null;
        if (account.isEncrypted() && account.getKeyCrypter() != null) {
            key = account.getKeyCrypter().deriveKey(password);
        }

        if (signMessage) {
            account.signMessage(message, key);
        } else {
            account.verifyMessage(message);
        }

        return message;
    }

    @Override abstract protected void onPostExecute(SignedMessage message);
}