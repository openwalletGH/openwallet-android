package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.coins.nxt.NxtException;
import com.coinomi.core.coins.nxt.Transaction;
import com.coinomi.core.exceptions.TransactionException;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.BlockHeader;
import com.coinomi.core.network.ServerClient;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.util.KeyUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.SendRequest;
import com.coinomi.core.wallet.SignedMessage;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.WalletAccountEventListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.wallet.RedeemData;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author John L. Jegutanis
 */
public class NxtFamilyWallet extends AbstractWallet<Transaction> implements TransactionEventListener<Transaction> {
    private static final Logger log = LoggerFactory.getLogger(NxtFamilyWallet.class);
    protected final Map<Sha256Hash, Transaction> transactions;
    @VisibleForTesting
    final HashMap<AbstractAddress, String> addressesStatus;
    @VisibleForTesting final transient ArrayList<AbstractAddress> addressesSubscribed;
    @VisibleForTesting final transient ArrayList<AbstractAddress> addressesPendingSubscription;
    @VisibleForTesting final transient HashMap<AbstractAddress, AddressStatus> statusPendingUpdates;
    @VisibleForTesting final transient HashSet<Sha256Hash> fetchingTransactions;
    private final NxtFamilyAddress address;
    NxtFamilyKey rootKey;
    private Value balance;
    private int lastEcBlockHeight;
    private long lastEcBlockId;
    // Wallet that this account belongs
    @Nullable private transient Wallet wallet = null;
    private BlockchainConnection<Transaction> blockchainConnection;
    @Nullable private Sha256Hash lastBlockSeenHash;
    private int lastBlockSeenHeight = -1;
    private long lastBlockSeenTimeSecs = 0;
    private List<ListenerRegistration<WalletAccountEventListener>> listeners;


    private Runnable saveLaterRunnable = new Runnable() {
        @Override
        public void run() {
            if (wallet != null) wallet.saveLater();
        }
    };

    private Runnable saveNowRunnable = new Runnable() {
        @Override
        public void run() {
            if (wallet != null) wallet.saveNow();
        }
    };

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
        log.info("nxt public key: {}", Convert.toHexString(key.getPublicKey()) );
        balance = type.value(0);
        addressesStatus = new HashMap<>();
        addressesSubscribed = new ArrayList<>();
        addressesPendingSubscription = new ArrayList<>();
        statusPendingUpdates = new HashMap<>();
        fetchingTransactions = new HashSet<>();
        transactions = new HashMap<>();
        listeners = new CopyOnWriteArrayList<>();
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

    @Override
    public void completeTransaction(SendRequest request) throws WalletAccountException {
        checkArgument(!request.isCompleted(), "Given SendRequest has already been completed.");

        /*if (request.type.getTransactionVersion() > 0) {
            request.nxtTxBuilder.ecBlockHeight(getLastEcBlockHeight());
            request.nxtTxBuilder.ecBlockId(getLastEcBlockId());
        }

        // TODO check if the destination public key was announced and if so, remove it from the tx:
        // request.nxtTxBuilder.publicKeyAnnouncement(null);

        try {
            request.nxtTx = request.nxtTxBuilder.build();
        } catch (NxtException.NotValidException e) {
            throw new WalletAccount.WalletAccountException(e);
        }*/
        request.setCompleted(true);
    }

    @Override
    public void signTransaction(SendRequest request) {
        checkArgument(request.isCompleted(), "Send request is not completed");
        checkArgument(request.tx != null, "No transaction found in send request");
        String nxtSecret;
        if (rootKey.isEncrypted()) {
            checkArgument(request.aesKey != null, "Wallet is encrypted but no decryption key provided");
            nxtSecret = rootKey.toDecrypted(request.aesKey).getPrivateKeyMnemonic();
        } else {
            nxtSecret = rootKey.getPrivateKeyMnemonic();
        }
        ((Transaction)request.tx).sign(nxtSecret);
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
        // TODO implement, how can we check if this account is new?
        return true;
    }

    @Override
    public Value getBalance() {
        return balance;
    }

    @Override
    public void refresh() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isConnected() {
        return blockchainConnection != null;
    }

    @Override
    public boolean isLoading() {
//        TODO implement
        return false;
    }

    @Override
    public AbstractAddress getChangeAddress() {
        return address;
    }

    @Override
    public AbstractAddress getReceiveAddress() {
        return address;
    }

    @Override
    public AbstractAddress getRefundAddress() {
        return address;
    }

    @Override
    public boolean hasUsedAddresses() {
        return false;
    }

    @Override
    public boolean canCreateNewAddresses() {
        return false;
    }

    @Override
    public Transaction getTransaction(String transactionId) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, Transaction> getUnspentTransactions() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, Transaction> getPendingTransactions() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, AbstractTransaction> getTransactions() {
        return new HashMap<>(); // TODO implement with Abstract Transactions
    }

    @Override
    public List<AbstractAddress> getActiveAddresses() {
        return ImmutableList.of((AbstractAddress) address);
    }

    @Override
    public void markAddressAsUsed(AbstractAddress address) { /* does not apply */ }

    @Override
    public Wallet getWallet() {
        return wallet;
    }

    @Override
    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
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
        SendRequest request = null;
        try {
            request = SendRequest.to(this, address, amount);
        } catch (Exception e) {
            throw new WalletAccountException(e);
        }
        request.aesKey = aesKey;

        return request;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Other stuff TODO implement
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addEventListener(WalletAccountEventListener listener) {
        // TODO implement
    }

    @Override
    public void addEventListener(WalletAccountEventListener listener, Executor executor) {
        // TODO implement
    }

    @Override
    public boolean removeEventListener(WalletAccountEventListener listener) {
        // TODO implement
        return false;
    }

    @Override
    public boolean isAddressMine(AbstractAddress address) {
        return false;
    }

    @Override
    public void maybeInitializeAllKeys() { /* Doesn't need initialization */ }

    @Override
    public void onConnection(BlockchainConnection blockchainConnection) {
        this.blockchainConnection = blockchainConnection;
        subscribeToBlockchain();

    }

    private void subscribeToBlockchain() {
        lock.lock();
        try {
            if (blockchainConnection != null) {
                blockchainConnection.subscribeToBlockchain(this);
            }
        } finally {
            lock.unlock();
        }
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
        log.info("Got a {} block: {}", type.getName(), header.getBlockHeight());

    }

    @Override
    public void onAddressStatusUpdate(AddressStatus status) {
        log.debug("Got a status {}", status);
        lock.lock();
        if (status.getStatus() != null) {
            if (isAddressStatusChanged(status)) {
                log.info("Must get transactions for address {}, status {}",
                        status.getAddress(), status.getStatus());

                if (blockchainConnection != null) {
                    blockchainConnection.getHistoryTx(status, this);
                }
            } else {
                log.info("Status {} already updating", status.getStatus());
            }
        }
    }

    private boolean isAddressStatusChanged(AddressStatus addressStatus) {
        lock.lock();
        try {
            AbstractAddress address = addressStatus.getAddress();
            String newStatus = addressStatus.getStatus();
            if (addressesStatus.containsKey(address)) {
                String previousStatus = addressesStatus.get(address);
                if (previousStatus == null) {
                    return newStatus != null; // Status changed if newStatus is not null
                } else {
                    return !previousStatus.equals(newStatus);
                }
            } else {
                // Unused address, just mark it that we watch it
                if (newStatus == null) {
                    commitAddressStatus(addressStatus);
                    return false;
                } else {
                    return true;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    void commitAddressStatus(AddressStatus newStatus) {
        lock.lock();
        try {
            AddressStatus updatingStatus = statusPendingUpdates.get(newStatus.getAddress());
            if (updatingStatus != null && updatingStatus.equals(newStatus)) {
                statusPendingUpdates.remove(newStatus.getAddress());
            }
            addressesStatus.put(newStatus.getAddress(), newStatus.getStatus());
        }
        finally {
            lock.unlock();
        }
        // Skip saving null statuses
        if (newStatus.getStatus() != null) {
            walletSaveLater();
        }
    }

    @Override
    public void onTransactionHistory(AddressStatus status, List<ServerClient.HistoryTx> historyTxes) {
        lock.lock();
        try {
            AddressStatus updatingStatus = statusPendingUpdates.get(status.getAddress());
            // Check if this updating status is valid
            if (updatingStatus != null && updatingStatus.equals(status)) {
                updatingStatus.queueHistoryTransactions(historyTxes);
                fetchTransactions(historyTxes);
                //tryToApplyState(updatingStatus);
            } else {
                log.info("Ignoring history tx call because no entry found or newer entry.");
            }
        }
        finally {
            lock.unlock();
        }
    }

    private void fetchTransactions(List<? extends ServerClient.HistoryTx> txes) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        for (ServerClient.HistoryTx tx : txes) {
            fetchTransactionIfNeeded(tx.getTxHash());
        }
    }

    private void fetchTransactionIfNeeded(Sha256Hash txHash) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        // Check if need to fetch the transaction
        if (!isTransactionAvailableOrQueued(txHash)) {
            log.info("Going to fetch transaction with hash {}", txHash);
            fetchingTransactions.add(txHash);
            if (blockchainConnection != null) {
                blockchainConnection.getTransaction(txHash, this);
            }
        }
    }

    private boolean isTransactionAvailableOrQueued(Sha256Hash txHash) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        return getTransaction(txHash) != null || fetchingTransactions.contains(txHash);
    }

    @Nullable
    public Transaction getTransaction(Sha256Hash hash) {
        lock.lock();
        try {
            return transactions.get(hash);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onTransactionUpdate(Transaction tx) {

    }

    @Override
    public void onTransactionBroadcast(Transaction transaction) {

    }

    @Override
    public void onTransactionBroadcastError(Transaction tx) {

    }
}
