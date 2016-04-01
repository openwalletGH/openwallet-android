package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.coins.nxt.NxtException;
import com.coinomi.core.coins.nxt.Transaction;
import com.coinomi.core.exceptions.TransactionBroadcastException;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.BlockHeader;
import com.coinomi.core.network.NxtServerClient;
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
import com.coinomi.core.wallet.WalletConnectivityStatus;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.RedeemData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
public class NxtFamilyWallet extends AbstractWallet<NxtTransaction, NxtAddress>
        implements TransactionEventListener<NxtTransaction> {
    private static final Logger log = LoggerFactory.getLogger(NxtFamilyWallet.class);
    protected final Map<Sha256Hash, NxtTransaction> rawtransactions;
    @VisibleForTesting
    final HashMap<AbstractAddress, String> addressesStatus;
    @VisibleForTesting final transient ArrayList<AbstractAddress> addressesSubscribed;
    @VisibleForTesting final transient ArrayList<AbstractAddress> addressesPendingSubscription;
    @VisibleForTesting final transient HashMap<AbstractAddress, AddressStatus> statusPendingUpdates;
    //@VisibleForTesting final transient HashSet<Sha256Hash> fetchingTransactions;
    private final NxtAddress address;
    NxtFamilyKey rootKey;
    private Value balance;
    private int lastEcBlockHeight;
    private long lastEcBlockId;
    // Wallet that this account belongs
    @Nullable private transient Wallet wallet = null;
    private NxtServerClient blockchainConnection;
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
        address = new NxtAddress(type, key.getPublicKey());
        balance = type.value(0);
        addressesStatus = new HashMap<>();
        addressesSubscribed = new ArrayList<>();
        addressesPendingSubscription = new ArrayList<>();
        statusPendingUpdates = new HashMap<>();
        //fetchingTransactions = new HashSet<>();
        rawtransactions = new HashMap<>();
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
    public SendRequest getEmptyWalletRequest(AbstractAddress destination) throws WalletAccountException {
        checkAddress(destination);
        return NxtSendRequest.emptyWallet(this, (NxtAddress) destination);
    }

    @Override
    public SendRequest getSendToRequest(AbstractAddress destination, Value amount) throws WalletAccountException {
        checkAddress(destination);
        return NxtSendRequest.to(this, (NxtAddress) destination, amount);
    }

    private void checkAddress(AbstractAddress destination) throws WalletAccountException {
        if (!(destination instanceof NxtAddress)) {
            throw new WalletAccountException("Incompatible address" +
                    destination.getClass().getName() + ", expected " + NxtAddress.class.getName());
        }
    }


    @Override
    public void completeTransaction(SendRequest request) throws WalletAccountException {
        checkSendRequest(request);
        completeTransaction((NxtSendRequest) request);
    }

    @Override
    public void signTransaction(SendRequest request) throws WalletAccountException {
        checkSendRequest(request);
        signTransaction((NxtSendRequest) request);
    }

    private void checkSendRequest(SendRequest request) throws WalletAccountException {
        if (!(request instanceof NxtSendRequest)) {
            throw new WalletAccountException("Incompatible request " +
                    request.getClass().getName() + ", expected " + NxtSendRequest.class.getName());
        }
    }

    public void completeTransaction(NxtSendRequest request) throws WalletAccountException {
        checkArgument(!request.isCompleted(), "Given SendRequest has already been completed.");

        if (request.type.getTransactionVersion() > 0) {
            request.nxtTxBuilder.ecBlockHeight(lastEcBlockHeight);
            request.nxtTxBuilder.ecBlockId(lastEcBlockId);
        }

        // TODO check if the destination public key was announced and if so, remove it from the tx:
        // request.nxtTxBuilder.publicKeyAnnouncement(null);

        try {
            request.tx = new NxtTransaction(type, request.nxtTxBuilder.build());
            request.setCompleted(true);
        } catch (NxtException.NotValidException e) {
            throw new WalletAccount.WalletAccountException(e);
        }

        if (request.signTransaction) {
            signTransaction(request);
        }
    }

    public void signTransaction(NxtSendRequest request) {
        checkArgument(request.isCompleted(), "Send request is not completed");
        checkArgument(request.tx != null, "No transaction found in send request");
        Transaction tx = request.tx.getRawTransaction();
        byte[] privateKey;
        if (rootKey.isEncrypted()) {
            checkArgument(request.aesKey != null, "Wallet is encrypted but no decryption key provided");
            privateKey = rootKey.toDecrypted(request.aesKey).getPrivateKey();
        } else {
            privateKey = rootKey.getPrivateKey();
        }
        tx.sign(privateKey);
        Arrays.fill(privateKey, (byte) 0); // clear private key
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
        return Convert.toHexString(getPublicKey());
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
        lock.lock();
        try {
            log.info("Refreshing wallet pocket {}", type);
            lastBlockSeenHash = null;
            lastBlockSeenHeight = -1;
            lastBlockSeenTimeSecs = 0;
            lastEcBlockHeight = 0;
            lastEcBlockId = 0;
            rawtransactions.clear();
            addressesStatus.clear();
            clearTransientState();
        } finally {
            lock.unlock();
        }
    }

    private void clearTransientState() {
        addressesSubscribed.clear();
        addressesPendingSubscription.clear();
        statusPendingUpdates.clear();
        //fetchingTransactions.clear();
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
    public void disconnect() {
        if (blockchainConnection != null) {
            blockchainConnection.stopAsync();
        }
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
    public NxtAddress getReceiveAddress(boolean isManualAddressManagement) {
        return this.address;
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
    public boolean broadcastTxSync(AbstractTransaction tx) throws TransactionBroadcastException {
        if (tx instanceof NxtTransaction) {
            return broadcastNxtTxSync((NxtTransaction) tx);
        } else {
            throw new TransactionBroadcastException("Unsupported transaction class: " +
                    tx.getClass().getName() + ", need: " + NxtTransaction.class.getName());
        }
    }

    public boolean broadcastNxtTxSync(NxtTransaction tx) throws TransactionBroadcastException {
        if (isConnected()) {
            if (log.isInfoEnabled()) {
                log.info("Broadcasting tx {}", Utils.HEX.encode(tx.getRawTransaction().getBytes()));
            }
            boolean success = blockchainConnection.broadcastTxSync(tx);
            if (success) {
                onTransactionBroadcast(tx);
            } else {
                onTransactionBroadcastError(tx);
            }
            return success;
        } else {
            throw new TransactionBroadcastException("No connection available");
        }
    }

    @Override
    public void broadcastTx(AbstractTransaction tx) throws TransactionBroadcastException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public AbstractAddress getRefundAddress(boolean isManualAddressManagement) {
        return address;
    }

    public Map<Sha256Hash, Transaction> getPendingRawTransactions() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, NxtTransaction> getTransactions() {
        lock.lock();
        try {
            return ImmutableMap.copyOf(rawtransactions);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public NxtTransaction getTransaction(String transactionId) {
        lock.lock();
        try {
            return rawtransactions.get(new Sha256Hash(transactionId));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<Sha256Hash, NxtTransaction> getPendingTransactions() {
        throw new RuntimeException("Not implemented");
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
//        throw new RuntimeException("Not implemented");
    }

    @Override
    public void walletSaveNow() {
//        throw new RuntimeException("Not implemented");
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
    public NxtSendRequest sendCoinsOffline(NxtAddress address, Value amount) throws WalletAccountException {
        return sendCoinsOffline(address, amount, (KeyParameter) null);
    }

    /**
     * {@link #sendCoinsOffline(NxtAddress, Value)}
     */
    public NxtSendRequest sendCoinsOffline(NxtAddress address, Value amount, @Nullable String password)
            throws WalletAccountException {
        KeyParameter key = null;
        if (password != null) {
            checkState(isEncrypted());
            key = checkNotNull(getKeyCrypter()).deriveKey(password);
        }
        return sendCoinsOffline(address, amount, key);
    }

    /**
     * {@link #sendCoinsOffline(NxtAddress, Value)}
     */
    public NxtSendRequest sendCoinsOffline(NxtAddress address, Value amount, @Nullable KeyParameter aesKey)
            throws WalletAccountException {
        NxtSendRequest request;
        try {
            request = NxtSendRequest.to(this, address, amount);
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
        addEventListener(listener, Threading.USER_THREAD);
    }

    @Override
    public void addEventListener(WalletAccountEventListener listener, Executor executor) {
        listeners.add(new ListenerRegistration<>(listener, executor));
    }

    @Override
    public boolean removeEventListener(WalletAccountEventListener listener) {
        return ListenerRegistration.removeFromList(listener, listeners);
    }

    @Override
    public boolean isAddressMine(AbstractAddress address) {
        return this.address.equals(address);
    }

    @Override
    public void maybeInitializeAllKeys() { /* Doesn't need initialization */ }

    @Override
    public void onConnection(BlockchainConnection blockchainConnection) {
        this.blockchainConnection = (NxtServerClient)blockchainConnection;
        subscribeToBlockchain();

        subscribeIfNeeded();
    }

    void subscribeIfNeeded() {
        lock.lock();
        try {
            if (blockchainConnection != null) {
                List<AbstractAddress> addressesToWatch = getAddressesToWatch();
                if (addressesToWatch.size() > 0) {
                    addressesPendingSubscription.addAll(addressesToWatch);
                    blockchainConnection.subscribeToAddresses(addressesToWatch, this);
                }
            }
        } catch (Exception e) {
            log.error("Error subscribing to addresses", e);
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting List<AbstractAddress> getAddressesToWatch() {
        ImmutableList.Builder<AbstractAddress> addressesToWatch = ImmutableList.builder();
        for (AbstractAddress address : getActiveAddresses()) {
            // If address not already subscribed or pending subscription
            if (!addressesSubscribed.contains(address) && !addressesPendingSubscription.contains(address)) {
                addressesToWatch.add(address);
            }
        }
        return addressesToWatch.build();
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
        blockchainConnection = null;
        queueOnConnectivity();
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
    public void onNewBlock(BlockHeader header) {
        log.info("Got a {} block: {}", type.getName(), header.getBlockHeight());
        boolean shouldSave = false;
        lock.lock();
        try {
            lastBlockSeenTimeSecs = header.getTimestamp();
            lastBlockSeenHeight = header.getBlockHeight();

            queueOnNewBlock();
        } finally {
            lock.unlock();
        }
        //if (shouldSave) walletSaveLater();
    }

    @Override
    public void onBlockUpdate(BlockHeader header) {
        // TODO should update transaction times if needed
    }

    void queueOnNewBlock() {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        for (final ListenerRegistration<WalletAccountEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onNewBlock(NxtFamilyWallet.this);
                    registration.listener.onWalletChanged(NxtFamilyWallet.this);
                }
            });
        }
    }

    @Override
    public void onAddressStatusUpdate(AddressStatus status) {
        log.debug("Got a status {}", status);
        lock.lock();
        try {
            if (status.getStatus() != null) {
                if (isAddressStatusChanged(status)) {
                    this.balance = Value.valueOf(this.type, Long.valueOf(status.getStatus()));
                    //if (registerStatusForUpdate(status)) {
                    log.info("Must get transactions for address {}, status {}",
                            status.getAddress(), status.getStatus());

                    if (blockchainConnection != null) {
                        blockchainConnection.getHistoryTx(status, this);
                    }
                }
                //} else {
                //    log.info("Status {} already updating", status.getStatus());
                //}
            } else {
                commitAddressStatus(status);
            }
        }
        finally {
            lock.unlock();
        }
    }

    /*
    @VisibleForTesting boolean registerStatusForUpdate(AddressStatus status) {
        checkNotNull(status.getStatus());

        lock.lock();
        try {
            // If current address is updating
            if (statusPendingUpdates.containsKey(status.getAddress())) {
                AddressStatus updatingAddressStatus = statusPendingUpdates.get(status.getAddress());
                String updatingStatus = updatingAddressStatus.getStatus();

                // If the same status is updating, don't update again
                if (updatingStatus != null && updatingStatus.equals(status.getStatus())) {
                    return false;
                } else { // Status is newer, so replace the updating status
                    statusPendingUpdates.put(status.getAddress(), status);
                    return true;
                }
            } else { // This status is new
                statusPendingUpdates.put(status.getAddress(), status);
                return true;
            }
        }
        finally {
            lock.unlock();
        }
    }*/

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
            /*AddressStatus updatingStatus = statusPendingUpdates.get(newStatus.getAddress());
            if (updatingStatus != null && updatingStatus.equals(newStatus)) {
                statusPendingUpdates.remove(newStatus.getAddress());
            }*/
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
        log.info("onTransactionHistory");
        lock.lock();
        try {
            //AddressStatus updatingStatus = statusPendingUpdates.get(status.getAddress());
            // Check if this updating status is valid
                status.queueHistoryTransactions(historyTxes);
                log.info("Fetching txs");
                fetchTransactions(historyTxes);
                queueOnNewBalance();
                //tryToApplyState(updatingStatus);
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
        log.info("Trying to fetch transaction with hash {}", txHash);
        if (!isTransactionAvailableOrQueued(txHash)) {
            log.info("Going to fetch transaction with hash {}", txHash);
            //fetchingTransactions.add(txHash);
            if (blockchainConnection != null) {
                blockchainConnection.getTransaction(txHash, this);
            }
        }
        else {
            log.info("cannot fetch tx with hash {}", txHash);
        }
    }

    private boolean isTransactionAvailableOrQueued(Sha256Hash txHash) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        return rawtransactions.containsKey(txHash);
    }

    @Nullable
    public synchronized Transaction getRawTransaction(Sha256Hash hash) {
        lock.lock();
        try {
            NxtTransaction tx = rawtransactions.get(hash);
            if (tx != null) {
                return tx.getRawTransaction();
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onTransactionUpdate(NxtTransaction tx) {
        if (log.isInfoEnabled()) log.info("Got a new transaction {}", tx.getHashAsString());
        lock.lock();
        try {
            addNewTransactionIfNeeded(tx);
        }
        finally {
            lock.unlock();
        }

    }


    @VisibleForTesting
    void addNewTransactionIfNeeded(NxtTransaction tx) {
        lock.lock();
        try {
            // If was fetching this tx, remove it
            //fetchingTransactions.remove(tx.getFullHash());
            log.info("adding transaction to wallet");
            // This tx not in wallet, add it
            NxtTransaction storedTx = rawtransactions.get(tx.getHash());
            if (storedTx == null) {
                log.info("transaction added");
                rawtransactions.put(tx.getHash(), tx);
                //tx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
                //addWalletTransaction(WalletTransaction.Pool.PENDING, tx, true);
                queueOnNewBalance();
            }
            else {
                storedTx.setDepthInBlocks(tx.getDepthInBlocks());
                storedTx.setAppearedAtChainHeight(tx.getAppearedAtChainHeight());
            }
        } finally {
            lock.unlock();
        }
    }

    void queueOnNewBalance() {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        final Value balance = getBalance();
        for (final ListenerRegistration<WalletAccountEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onNewBalance(balance);
                    registration.listener.onWalletChanged(NxtFamilyWallet.this);
                }
            });
        }
    }

    void queueOnConnectivity() {
        final WalletConnectivityStatus connectivity = getConnectivityStatus();
        for (final ListenerRegistration<WalletAccountEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onConnectivityStatus(connectivity);
                    registration.listener.onWalletChanged(NxtFamilyWallet.this);
                }
            });
        }
    }

    @Override
    public void onTransactionBroadcast(NxtTransaction tx) {
        lock.lock();
        try {
            log.info("Transaction sent {}", tx);
            //FIXME, when enabled it breaks the transactions connections and we get an incorrect coin balance
            addNewTransactionIfNeeded(tx);
        } finally {
            lock.unlock();
        }
        //queueOnTransactionBroadcastSuccess(tx);
    }

    @Override
    public void onTransactionBroadcastError(NxtTransaction tx) {
        //queueOnTransactionBroadcastFailure(tx);
    }
}
