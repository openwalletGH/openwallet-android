package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.ValueType;
import com.coinomi.core.network.interfaces.ConnectionEventListener;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionBag;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.KeyBag;

import org.spongycastle.crypto.params.KeyParameter;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public interface WalletAccount<T> extends TransactionBag, KeyBag,
        ConnectionEventListener, Serializable {
    class WalletAccountException extends Exception {
        public WalletAccountException(Throwable cause) {
            super(cause);
        }
    }

    String getId();
    String getDescription();
    void setDescription(String description);
    byte[] getPublicKey();
    CoinType getCoinType();

    boolean isNew();

    Value getBalance();

    void refresh();

    boolean isConnected();
    boolean isLoading();
    WalletPocketConnectivity getConnectivityStatus();

    /**
     * Returns the address used for change outputs. Note: this will probably go away in future.
     */
    AbstractAddress getChangeAddress();

    /**
     * Get current receive address, does not mark it as used.
     */
    AbstractAddress getReceiveAddress();

    /**
     * Get current refund address, does not mark it as used.
     *
     * Notice: This address could be the same as the current receive address
     */
    AbstractAddress getRefundAddress(boolean isManualAddressManagement);

    AbstractAddress getReceiveAddress(boolean isManualAddressManagement) ;


    /**
     * Returns true if this wallet has previously used addresses
     */
    boolean hasUsedAddresses();


    boolean broadcastTxSync(AbstractTransaction tx) throws IOException;

    void broadcastTx(AbstractTransaction tx) throws IOException;

    /**
     * Returns true if this wallet can create new addresses
     */
    boolean canCreateNewAddresses();

    T getTransaction(String transactionId);
    Map<Sha256Hash, T> getUnspentTransactions();
    Map<Sha256Hash, T> getPendingTransactions();
    Map<Sha256Hash, AbstractTransaction> getAbstractTransactions();

    List<AbstractAddress> getActiveAddresses();
    void markAddressAsUsed(AbstractAddress address);

    void setWallet(Wallet wallet);

    Wallet getWallet();

    void walletSaveLater();
    void walletSaveNow();

    boolean isEncryptable();
    boolean isEncrypted();
    KeyCrypter getKeyCrypter();
    void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey);
    void decrypt(KeyParameter aesKey);

    boolean equals(WalletAccount otherAccount);

    void addEventListener(WalletAccountEventListener listener);
    void addEventListener(WalletAccountEventListener listener, Executor executor);
    boolean removeEventListener(WalletAccountEventListener listener);

    boolean isType(WalletAccount other);
    boolean isType(ValueType type);
    boolean isType(AbstractAddress address);

    boolean isAddressMine(AbstractAddress address);

    void maybeInitializeAllKeys();

    String getPublicKeyMnemonic();
    String getPrivateKeyMnemonic();

    void completeAndSignTx(SendRequest request) throws WalletAccountException;
    void completeTransaction(SendRequest request) throws WalletAccountException;
    void signTransaction(SendRequest request);

    void signMessage(SignedMessage unsignedMessage, @Nullable KeyParameter aesKey);
    void verifyMessage(SignedMessage signedMessage);

    String getPublicKeySerialized();
}
