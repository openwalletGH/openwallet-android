package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.Preconditions;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.ValueType;
import com.coinomi.core.coins.nxt.Appendix;
import com.coinomi.core.coins.nxt.Attachment;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.coins.nxt.NxtException;
import com.coinomi.core.coins.nxt.Transaction;
import com.coinomi.core.coins.nxt.TransactionImpl;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.BlockHeader;
import com.coinomi.core.network.ServerClient;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.util.KeyUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.SendRequest;
import com.coinomi.core.wallet.SignedMessage;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.WalletAccountEventListener;
import com.coinomi.core.wallet.WalletPocketConnectivity;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.RedeemData;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author John L. Jegutanis
 */
final public class NxtFamilyWallet extends AbstractWallet {
    private static final Logger log = LoggerFactory.getLogger(NxtFamilyWallet.class);

    NxtFamilyKey rootKey;
    private final NxtFamilyAddress address;
    // Wallet that this account belongs
    @Nullable private transient Wallet wallet = null;
    private int lastEcBlockHeight;
    private long lastEcBlockId;

    public NxtFamilyWallet(DeterministicKey entropy, CoinType type) {
        this(entropy, type, null, null);
    }

    public NxtFamilyWallet(DeterministicKey entropy, CoinType type,
                           @Nullable KeyCrypter keyCrypter, @Nullable KeyParameter key) {
        this(new NxtFamilyKey(entropy, keyCrypter, key), type);
    }

    public NxtFamilyWallet(NxtFamilyKey key, CoinType type) {
        this(KeyUtils.getPublicKeyId(type, key.getPublicKey()), key, type);
    }

    public NxtFamilyWallet(String id, NxtFamilyKey key, CoinType type) {
        super(type, id);
        rootKey = key;
        address = new NxtFamilyAddress(type, key.getPublicKey());
    }

    @Override
    public byte[] getPublicKey() {
        return rootKey.getPublicKey();
    }

    @Override
    public String getPublicKeyMnemonic() {
        return address.getRsAccount();
    }

    @Override
    public String getPrivateKeyMnemonic() {
        return rootKey.getPrivateKeyMnemonic();
    }

    @Override
    public void completeTransaction(SendRequest request) throws WalletAccountException {
        checkArgument(!request.isCompleted(), "Given SendRequest has already been completed.");

        if (request.type.getTransactionVersion() > 0) {
            request.nxtTxBuilder.ecBlockHeight(getLastEcBlockHeight());
            request.nxtTxBuilder.ecBlockId(getLastEcBlockId());
        }

        // TODO check if the destination public key was announced and if so, remove it from the tx:
        // request.nxtTxBuilder.publicKeyAnnouncement(null);

        try {
            request.nxtTx = request.nxtTxBuilder.build();
        } catch (NxtException.NotValidException e) {
            throw new WalletAccount.WalletAccountException(e);
        }
        request.setCompleted(true);
    }

    @Override
    public void signTransaction(SendRequest request) {
        checkArgument(request.isCompleted(), "Send request is not completed");
        checkArgument(request.nxtTx != null, "No transaction found in send request");
        String nxtSecret;
        if (rootKey.isEncrypted()) {
            checkArgument(request.aesKey != null, "Wallet is encrypted but no decryption key provided");
            nxtSecret = rootKey.toDecrypted(request.aesKey).getPrivateKeyMnemonic();
        } else {
            nxtSecret = rootKey.getPrivateKeyMnemonic();
        }
        request.nxtTx.sign(nxtSecret);
    }

    @Override
    public void signMessage(SignedMessage unsignedMessage, @Nullable KeyParameter aesKey) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void verifyMessage(SignedMessage signedMessage) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getPublicKeySerialized() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isNew() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Value getBalance() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void refresh() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isConnected() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public WalletPocketConnectivity getConnectivityStatus() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public AbstractAddress getChangeAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public AbstractAddress getReceiveAddress() {
        return address;
    }

    @Override
    public AbstractAddress getRefundAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Address getChangeBitAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Address getReceiveBitAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Address getRefundBitAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public org.bitcoinj.core.Transaction getTransaction(String transactionId) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, org.bitcoinj.core.Transaction> getUnspentTransactions() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, org.bitcoinj.core.Transaction> getPendingTransactions() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, org.bitcoinj.core.Transaction> getTransactions() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public List<Address> getActiveAddresses() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void markAddressAsUsed(Address address) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    @Override
    public Wallet getWallet() {
        return wallet;
    }

    @Override
    public void walletSaveLater() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void walletSaveNow() {
        throw new RuntimeException("Not implemented");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Serialization support
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    List<Protos.Key> serializeKeychainToProtobuf() {
        lock.lock();
        try {
            return rootKey.toProtobuf();
        } finally {
            lock.unlock();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Encryption support
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isEncryptable() {
        return true;
    }

    @Override
    public boolean isEncrypted() {
        lock.lock();
        try {
            return rootKey.isEncrypted();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public KeyCrypter getKeyCrypter() {
        lock.lock();
        try {
            return rootKey.getKeyCrypter();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        checkNotNull(keyCrypter);
        checkNotNull(aesKey);

        lock.lock();
        try {
            this.rootKey = this.rootKey.toEncrypted(keyCrypter, aesKey);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void decrypt(KeyParameter aesKey) {
        throw new RuntimeException("Not implemented");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Transaction signing support
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sends coins to the given address but does not broadcast the resulting pending transaction.
     */
    public SendRequest sendCoinsOffline(NxtFamilyAddress address, Value amount) throws WalletAccountException {
        return sendCoinsOffline(address, amount, (KeyParameter) null);
    }

    /**
     * {@link #sendCoinsOffline(NxtFamilyAddress, Value)}
     */
    public SendRequest sendCoinsOffline(NxtFamilyAddress address, Value amount, @Nullable String password)
            throws WalletAccountException {
        KeyParameter key = null;
        if (password != null) {
            checkState(isEncrypted());
            key = checkNotNull(getKeyCrypter()).deriveKey(password);
        }
        return sendCoinsOffline(address, amount, key);
    }

    /**
     * {@link #sendCoinsOffline(NxtFamilyAddress, Value)}
     */
    public SendRequest sendCoinsOffline(NxtFamilyAddress address, Value amount, @Nullable KeyParameter aesKey)
            throws WalletAccountException {
        SendRequest request = SendRequest.to(this, address, amount);
        request.aesKey = aesKey;

        return request;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Other stuff TODO implement
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public boolean equals(WalletAccount otherAccount) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void addEventListener(WalletAccountEventListener listener) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void addEventListener(WalletAccountEventListener listener, Executor executor) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean removeEventListener(WalletAccountEventListener listener) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isType(WalletAccount other) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isType(ValueType type) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isType(Address address) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isAddressMine(Address address) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isLoading() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void maybeInitializeAllKeys() { /* Doesn't need initialization */ }

    @Override
    public void onConnection(BlockchainConnection blockchainConnection) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onDisconnect() {
        throw new RuntimeException("Not implemented");
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
        throw new RuntimeException("Not implemented");
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubKey(byte[] pubkey) {
        throw new RuntimeException("Not implemented");
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isPubKeyHashMine(byte[] pubkeyHash) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isWatchedScript(Script script) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isPubKeyMine(byte[] pubkey) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isPayToScriptHashMine(byte[] payToScriptHash) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, org.bitcoinj.core.Transaction> getTransactionPool(WalletTransaction.Pool pool) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onNewBlock(BlockHeader header) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onAddressStatusUpdate(AddressStatus status) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onTransactionHistory(AddressStatus status, List<ServerClient.HistoryTx> historyTxes) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onTransactionUpdate(org.bitcoinj.core.Transaction tx) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onTransactionBroadcast(org.bitcoinj.core.Transaction transaction) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onTransactionBroadcastError(org.bitcoinj.core.Transaction tx) {
        throw new RuntimeException("Not implemented");
    }

    public int getLastEcBlockHeight() {
        return lastEcBlockHeight;
    }

    public void setLastEcBlockHeight(int lastEcBlockHeight) {
        this.lastEcBlockHeight = lastEcBlockHeight;
    }

    public long getLastEcBlockId() {
        return lastEcBlockId;
    }

    public void setLastEcBlockId(long lastEcBlockId) {
        this.lastEcBlockId = lastEcBlockId;
    }
}
