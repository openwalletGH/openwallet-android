package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.ValueType;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.BlockHeader;
import com.coinomi.core.network.ServerClient;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
abstract public class TransactionWatcherWallet implements WalletAccount {
    private static final Logger log = LoggerFactory.getLogger(TransactionWatcherWallet.class);

    private final static int TX_DEPTH_SAVE_THRESHOLD = 4;

    final ReentrantLock lock = Threading.lock("TransactionWatcherWallet");
    protected final CoinType coinType;

    @Nullable private Sha256Hash lastBlockSeenHash;
    private int lastBlockSeenHeight = -1;
    private long lastBlockSeenTimeSecs = 0;

    // Holds the status of every address we are watching. When connecting to the server, if we get a
    // different status for a particular address this means that there are new transactions for that
    // address and we have to fetch them. The status String could be null when an address is unused.
    @VisibleForTesting
    final HashMap<Address, String> addressesStatus;

    @VisibleForTesting final transient ArrayList<Address> addressesSubscribed;
    @VisibleForTesting final transient ArrayList<Address> addressesPendingSubscription;
    @VisibleForTesting final transient HashMap<Address, AddressStatus> statusPendingUpdates;
    @VisibleForTesting final transient HashSet<Sha256Hash> fetchingTransactions;

    // The various pools below give quick access to wallet-relevant transactions by the state they're in:
    //
    // Pending:  Transactions that didn't make it into the best chain yet. Pending transactions can be killed if a
    //           double-spend against them appears in the best chain, in which case they move to the dead pool.
    //           If a double-spend appears in the pending state as well, currently we just ignore the second
    //           and wait for the miners to resolve the race.
    // Unspent:  Transactions that appeared in the best chain and have outputs we can spend. Note that we store the
    //           entire transaction in memory even though for spending purposes we only really need the outputs, the
    //           reason being that this simplifies handling of re-orgs. It would be worth fixing this in future.
    // Spent:    Transactions that appeared in the best chain but don't have any spendable outputs. They're stored here
    //           for history browsing/auditing reasons only and in future will probably be flushed out to some other
    //           kind of cold storage or just removed.
    // Dead:     Transactions that we believe will never confirm get moved here, out of pending. Note that the Satoshi
    //           client has no notion of dead-ness: the assumption is that double spends won't happen so there's no
    //           need to notify the user about them. We take a more pessimistic approach and try to track the fact that
    //           transactions have been double spent so applications can do something intelligent (cancel orders, show
    //           to the user in the UI, etc). A transaction can leave dead and move into spent/unspent if there is a
    //           re-org to a chain that doesn't include the double spend.

    @VisibleForTesting final Map<Sha256Hash, Transaction> pending;
    @VisibleForTesting final Map<Sha256Hash, Transaction> unspent;
    @VisibleForTesting final Map<Sha256Hash, Transaction> spent;
    @VisibleForTesting final Map<Sha256Hash, Transaction> dead;

    // All transactions together.
    protected final Map<Sha256Hash, Transaction> transactions;
    private BlockchainConnection blockchainConnection;
    private List<ListenerRegistration<WalletAccountEventListener>> listeners;

    // Wallet that this account belongs
    @Nullable private transient Wallet wallet = null;

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

    // Constructor
    public TransactionWatcherWallet(CoinType coinType) {
        this.coinType = coinType;
        addressesStatus = new HashMap<Address, String>();
        addressesSubscribed = new ArrayList<Address>();
        addressesPendingSubscription = new ArrayList<Address>();
        statusPendingUpdates = new HashMap<Address, AddressStatus>();
        fetchingTransactions = new HashSet<Sha256Hash>();
        unspent = new HashMap<Sha256Hash, Transaction>();
        spent = new HashMap<Sha256Hash, Transaction>();
        pending = new HashMap<Sha256Hash, Transaction>();
        dead = new HashMap<Sha256Hash, Transaction>();
        transactions = new HashMap<Sha256Hash, Transaction>();
        listeners = new CopyOnWriteArrayList<ListenerRegistration<WalletAccountEventListener>>();
    }

    @Override
    public boolean isType(WalletAccount other) {
        return other != null && coinType.equals(other.getCoinType());
    }

    @Override
    public boolean isType(ValueType otherType) {
        return otherType != null && coinType.equals(otherType);
    }

    @Override
    public boolean isType(Address address) {
        return address != null && coinType.equals(address.getParameters());
    }

    @Override
    public CoinType getCoinType() {
        return coinType;
    }

    @Override
    public boolean isNew() {
        return unspent.size() + spent.size() + pending.size() == 0;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }

    // Util
    @Override
    public void walletSaveLater() {
        // Save in another thread to avoid cyclic locking of Wallet and WalletPocket
        Threading.USER_THREAD.execute(saveLaterRunnable);
    }

    @Override
    public void walletSaveNow() {
        // Save in another thread to avoid cyclic locking of Wallet and WalletPocket
        Threading.USER_THREAD.execute(saveNowRunnable);
    }

    /**
     * Returns a set of all transactions in the wallet.
     * @param includeDead     If true, transactions that were overridden by a double spend are included.
     */
    public Set<Transaction> getTransactions(boolean includeDead) {
        lock.lock();
        try {
            Set<Transaction> all = new HashSet<Transaction>();
            all.addAll(unspent.values());
            all.addAll(spent.values());
            all.addAll(pending.values());
            if (includeDead)
                all.addAll(dead.values());
            return all;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a set of all WalletTransactions in the wallet.
     */
    public Iterable<WalletTransaction> getWalletTransactions() {
        lock.lock();
        try {
            Set<WalletTransaction> all = new HashSet<WalletTransaction>();
            addWalletTransactionsToSet(all, WalletTransaction.Pool.UNSPENT, unspent.values());
            addWalletTransactionsToSet(all, WalletTransaction.Pool.SPENT, spent.values());
            addWalletTransactionsToSet(all, WalletTransaction.Pool.DEAD, dead.values());
            addWalletTransactionsToSet(all, WalletTransaction.Pool.PENDING, pending.values());
            return all;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Just adds the transaction to a pool without doing anything else
     * @param pool
     * @param tx
     */
    private void simpleAddTransaction(WalletTransaction.Pool pool, Transaction tx) {
        lock.lock();
        try {
            transactions.put(tx.getHash(), tx);
            switch (pool) {
                case UNSPENT:
                    checkState(unspent.put(tx.getHash(), tx) == null);
                    break;
                case SPENT:
                    checkState(spent.put(tx.getHash(), tx) == null);
                    break;
                case PENDING:
                    checkState(pending.put(tx.getHash(), tx) == null);
                    break;
                case DEAD:
                    checkState(dead.put(tx.getHash(), tx) == null);
                    break;
                default:
                    throw new RuntimeException("Unknown wallet transaction type " + pool);
            }
        } finally {
            lock.unlock();
        }
    }

    private static void addWalletTransactionsToSet(Set<WalletTransaction> txs,
                                                   WalletTransaction.Pool poolType, Collection<Transaction> pool) {
        for (Transaction tx : pool) {
            txs.add(new WalletTransaction(poolType, tx));
        }
    }

    /**
     * Adds a transaction that has been associated with a particular wallet pool. This is intended for usage by
     * deserialization code, such as the {@link WalletPocketProtobufSerializer} class. It isn't normally useful for
     * applications. It does not trigger auto saving.
     */
    public void addWalletTransaction(WalletTransaction wtx) {
        lock.lock();
        try {
            addWalletTransaction(wtx.getPool(), wtx.getTransaction(), true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marks outputs as spent, if we don't have the keys
     */
    private void markNotOwnOutputs(Transaction transaction) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        for (TransactionOutput txo : transaction.getOutputs()) {
            if (txo.isAvailableForSpending()) {
                // We don't have keys for this txo therefore it is not ours
                try {
                    if (findKeyFromPubHash(txo.getScriptPubKey().getPubKeyHash()) == null) {
                        txo.markAsSpent(null);
                    }
                } catch (ScriptException ignore) {
                    // If we don't understand this output, don't use it
                    txo.markAsSpent(null);
                }
            }
        }
    }

    /**
     * Adds the given transaction to the given pools and registers a confidence change listener on it.
     */
    private void addWalletTransaction(WalletTransaction.Pool pool, Transaction tx, boolean save) {
        lock.lock();
        try {
            if (log.isInfoEnabled()) {
                log.info("Adding {} tx to {} pool ({})",
                        tx.isEveryOwnedOutputSpent(this) ? WalletTransaction.Pool.SPENT : WalletTransaction.Pool.UNSPENT, pool, tx.getHash());
            }

            simpleAddTransaction(pool, tx);

            markNotOwnOutputs(tx);
            connectTransaction(tx);
            queueOnNewBalance();
        } finally {
            lock.unlock();
        }

        // This is safe even if the listener has been added before, as TransactionConfidence ignores duplicate
        // registration requests. That makes the code in the wallet simpler.
        // TODO add txConfidenceListener
//        tx.getConfidence().addEventListener(txConfidenceListener, Threading.SAME_THREAD);
        if (save) walletSaveLater();
    }


    /**
     * Returns a transaction object given its hash, if it exists in this wallet, or null otherwise.
     */
    @Nullable
    public Transaction getTransaction(String transactionId) {
        return getTransaction(new Sha256Hash(transactionId));
    }

    /**
     * Returns a transaction object given its hash, if it exists in this wallet, or null otherwise.
     */
    @Nullable
    public Transaction getTransaction(Sha256Hash hash) {
        lock.lock();
        try {
            return transactions.get(hash);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns transactions that match the hashes, some transactions could be missing.
     */
    public HashMap<Sha256Hash, Transaction> getTransactions(HashSet<Sha256Hash> hashes) {
        lock.lock();
        try {
            HashMap<Sha256Hash, Transaction> txs = new HashMap<Sha256Hash, Transaction>();
            for (Sha256Hash hash : hashes) {
                if (transactions.containsKey(hash)) {
                    txs.put(hash, transactions.get(hash));
                }
            }
            return txs;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Deletes transactions which appeared above the given block height from the wallet, but does not touch the keys.
     * This is useful if you have some keys and wish to replay the block chain into the wallet in order to pick them up.
     * Triggers auto saving.
     */
    @Override
    public void refresh() {
        lock.lock();
        try {
            log.info("Refreshing wallet pocket {}", coinType);
            lastBlockSeenHash = null;
            lastBlockSeenHeight = -1;
            lastBlockSeenTimeSecs = 0;
            unspent.clear();
            spent.clear();
            pending.clear();
            dead.clear();
            transactions.clear();
            addressesStatus.clear();
            clearTransientState();
        } finally {
            lock.unlock();
        }
    }

    /** Returns the hash of the last seen best-chain block, or null if the wallet is too old to store this data. */
    @Nullable
    public Sha256Hash getLastBlockSeenHash() {
        lock.lock();
        try {
            return lastBlockSeenHash;
        } finally {
            lock.unlock();
        }
    }

    public void setLastBlockSeenHash(@Nullable Sha256Hash lastBlockSeenHash) {
        lock.lock();
        try {
            this.lastBlockSeenHash = lastBlockSeenHash;
        } finally {
            lock.unlock();
        }
        walletSaveLater();
    }

    public void setLastBlockSeenHeight(int lastBlockSeenHeight) {
        lock.lock();
        try {
            this.lastBlockSeenHeight = lastBlockSeenHeight;
        } finally {
            lock.unlock();
        }
        walletSaveLater();
    }

    public void setLastBlockSeenTimeSecs(long timeSecs) {
        lock.lock();
        try {
            lastBlockSeenTimeSecs = timeSecs;
        } finally {
            lock.unlock();
        }
        walletSaveLater();
    }

    /**
     * Returns the UNIX time in seconds since the epoch extracted from the last best seen block header. This timestamp
     * is <b>not</b> the local time at which the block was first observed by this application but rather what the block
     * (i.e. miner) self declares. It is allowed to have some significant drift from the real time at which the block
     * was found, although most miners do use accurate times. If this wallet is old and does not have a recorded
     * time then this method returns zero.
     */
    public long getLastBlockSeenTimeSecs() {
        lock.lock();
        try {
            return lastBlockSeenTimeSecs;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a {@link java.util.Date} representing the time extracted from the last best seen block header. This timestamp
     * is <b>not</b> the local time at which the block was first observed by this application but rather what the block
     * (i.e. miner) self declares. It is allowed to have some significant drift from the real time at which the block
     * was found, although most miners do use accurate times. If this wallet is old and does not have a recorded
     * time then this method returns null.
     */
    @Nullable
    public Date getLastBlockSeenTime() {
        final long secs = getLastBlockSeenTimeSecs();
        if (secs == 0)
            return null;
        else
            return new Date(secs * 1000);
    }

    /**
     * Returns the height of the last seen best-chain block. Can be 0 if a wallet is brand new or -1 if the wallet
     * is old and doesn't have that data.
     */
    public int getLastBlockSeenHeight() {
        lock.lock();
        try {
            return lastBlockSeenHeight;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Value getBalance() {
        lock.lock();
        try {
            return getTxBalance(Iterables.concat(unspent.values(), pending.values()), true);
        } finally {
            lock.unlock();
        }
    }

    Value getTxBalance(Iterable<Transaction> txs, boolean toMe) {
        lock.lock();
        try {
            Value value = coinType.value(0);
            for (Transaction tx : txs) {
                if (toMe) {
                    value = value.add(tx.getValueSentToMe(this, false));
                } else {
                    value = value.add(tx.getValue(this));
                }
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets that the specified status is currently updating i.e. getting transactions.
     *
     * Returns true if registered successfully or false if status already updating
     */
    @VisibleForTesting boolean registerStatusForUpdate(AddressStatus status) {
        checkNotNull(status.getStatus());

        lock.lock();
        try {
            // If current address is updating
            if (statusPendingUpdates.containsKey(status.getAddress())) {
                AddressStatus updatingStatus = statusPendingUpdates.get(status.getAddress());

                // If the same status is updating, don't update again
                if (updatingStatus.getStatus().equals(status.getStatus())) {
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

    private boolean isAddressStatusChanged(AddressStatus addressStatus) {
        lock.lock();
        try {
            Address address = addressStatus.getAddress();
            String newStatus = addressStatus.getStatus();
            if (addressesStatus.containsKey(address)) {
                String previousStatus = addressesStatus.get(address);
                if (previousStatus == null) {
                    return newStatus != null; // Status changed if newStatus is not null
                } else {
                    return !previousStatus.equals(newStatus);
                }
            }
            else {
                // Unused address, just mark it that we watch it
                if (newStatus == null) {
                    commitAddressStatus(addressStatus);
                    return false;
                }
                else {
                    return true;
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    @Nullable
    public AddressStatus getAddressStatus(Address address) {
        lock.lock();
        try {
            if (addressesStatus.containsKey(address)) {
                return new AddressStatus(address, addressesStatus.get(address));
            }
            else {
                return null;
            }
        }
        finally {
            lock.unlock();
        }
    }

    public List<AddressStatus> getAllAddressStatus() {
        lock.lock();
        try {
            ArrayList<AddressStatus> statuses = new ArrayList<AddressStatus>(addressesStatus.size());
            for (Map.Entry<Address, String> status : addressesStatus.entrySet()) {
                statuses.add(new AddressStatus(status.getKey(), status.getValue()));
            }
            return statuses;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Returns all the addresses that are not currently watched
     */
    @VisibleForTesting List<Address> getAddressesToWatch() {
        ImmutableList.Builder<Address> addressesToWatch = ImmutableList.builder();
        for (Address address : getActiveAddresses()) {
            // If address not already subscribed or pending subscription
            if (!addressesSubscribed.contains(address) && !addressesPendingSubscription.contains(address)) {
                addressesToWatch.add(address);
            }
        }
        return addressesToWatch.build();
    }

    private void confirmAddressSubscription(Address address) {
        lock.lock();
        try {
            if (addressesPendingSubscription.contains(address)) {
                log.debug("Subscribed to {}", address);
                addressesPendingSubscription.remove(address);
                addressesSubscribed.add(address);
            }
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void onNewBlock(BlockHeader header) {
        log.info("Got a {} block: {}", coinType.getName(), header.getBlockHeight());
        boolean shouldSave = false;
        lock.lock();
        try {
            lastBlockSeenTimeSecs = header.getTimestamp();
            lastBlockSeenHeight = header.getBlockHeight();
            for (Transaction tx : getTransactions(false)) {
                TransactionConfidence confidence = tx.getConfidence();
                // Save wallet when we have new TXs
                if (confidence.getDepthInBlocks() < TX_DEPTH_SAVE_THRESHOLD) shouldSave = true;
                maybeUpdateBlockDepth(confidence);
            }
            queueOnNewBlock();
        } finally {
            lock.unlock();
        }
        if (shouldSave) walletSaveLater();
    }

    private void maybeUpdateBlockDepth(TransactionConfidence confidence) {
        if (confidence.getConfidenceType() != TransactionConfidence.ConfidenceType.BUILDING) return;
        int newDepth = lastBlockSeenHeight - confidence.getAppearedAtChainHeight() + 1;
        if (newDepth > 1) confidence.setDepthInBlocks(newDepth);
    }

    @Override
    public void onAddressStatusUpdate(AddressStatus status) {
        log.debug("Got a status {}", status);
        lock.lock();
        try {
            confirmAddressSubscription(status.getAddress());
            if (status.getStatus() != null) {
                markAddressAsUsed(status.getAddress());
                subscribeIfNeeded();

                if (isAddressStatusChanged(status)) {
                    // Status changed, time to update
                    if (registerStatusForUpdate(status)) {
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
            else {
                // Address not used, just update the status
                commitAddressStatus(status);
            }
        }
        finally {
            lock.unlock();
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
                tryToApplyState(updatingStatus);
            } else {
                log.info("Ignoring history tx call because no entry found or newer entry.");
            }
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Try to apply all address states
     */
    private void tryToApplyState() {
        lock.lock();
        try {
            // Make a copy of statusPendingUpdates.values() because we modify it later
            for (AddressStatus status : Lists.newArrayList(statusPendingUpdates.values())) {
                tryToApplyState(status);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Try to apply the status state
     */
    private void tryToApplyState(AddressStatus status) {
        lock.lock();
        try {
            if (statusPendingUpdates.containsKey(status.getAddress()) && status.isReady()) {
                HashSet<Sha256Hash> txHashes = status.getAllTransactionHashes();
                HashMap<Sha256Hash, Transaction> txs = getTransactions(txHashes);
                // We have all the transactions, apply state
                if (txs.size() == txHashes.size()) {
                    applyState(status, txs);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void applyState(AddressStatus status, HashMap<Sha256Hash, Transaction> txs) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        log.info("Applying state {} - {}", status.getAddress(), status.getStatus());
        // Connect inputs to outputs
        for (ServerClient.HistoryTx historyTx : status.getHistoryTxs()) {
            Transaction tx = txs.get(historyTx.getTxHash());
            if (tx != null) {
                log.info("{} getHeight() = " + historyTx.getHeight(), historyTx.getTxHash());
                if (historyTx.getHeight() > 0 && tx.getConfidence().getDepthInBlocks() == 0) {
                    TransactionConfidence confidence = tx.getConfidence();
                    confidence.setAppearedAtChainHeight(historyTx.getHeight());
                    maybeUpdateBlockDepth(confidence);
                }
            } else {
                log.error("Could not find {} in the transactions pool. Aborting applying state",
                        historyTx.getTxHash());
                return;
            }
        }

        for (Transaction tx : txs.values()) {
            connectTransaction(tx);
        }

        commitAddressStatus(status);
        queueOnNewBalance();
    }

    private void connectTransaction(Transaction tx) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        // Connect to other transactions in the wallet pocket
        if (log.isInfoEnabled()) log.info("Connecting inputs of tx {}", tx.getHash());
        int txiIndex = 0;
        for (TransactionInput txi : tx.getInputs()) {
            TransactionOutput output = txi.getConnectedOutput();
            if (output != null && !output.isAvailableForSpending()) {
                // Check if the current input spends this output
                if (output.getSpentBy() == null || output.getSpentBy().equals(txi)) {
                    log.info("skipping an already connected txi {}", txi);
                    txiIndex++;
                    continue; // skip connected inputs
                }
            }
            Sha256Hash outputHash = txi.getOutpoint().getHash();
            Transaction fromTx = transactions.get(outputHash);
            if (fromTx != null) {
                // Try to connect and recover if failed once.
                for (int i = 2; i > 0; i--) {
                    TransactionInput.ConnectionResult result = txi.connect(fromTx, TransactionInput.ConnectMode.DISCONNECT_ON_CONFLICT);
                    if (result == TransactionInput.ConnectionResult.NO_SUCH_TX) {
                        log.error("Could not connect {} to {}", txi.getOutpoint(), fromTx.getHash());
                    } else if (result == TransactionInput.ConnectionResult.ALREADY_SPENT) {
                        TransactionOutput out = fromTx.getOutput((int) txi.getOutpoint().getIndex());
                        log.warn("Already spent {}, forcing unspent and retry", out);
                        out.markAsUnspent();
                    } else {
                        if (log.isInfoEnabled()) {
                            log.info("Connected {}:{} to {}:{}", fromTx.getHash(),
                                    txi.getOutpoint().getIndex(), tx.getHashAsString(), txiIndex);

                        }
                        break; // No errors, break the loop
                    }
                }
                // Could become spent, maybe change pool
                maybeMovePool(fromTx);
            }
            else {
                log.info("No output found for input {}:{}", tx.getHashAsString(),
                        txiIndex);
            }
            txiIndex++;
        }
        maybeMovePool(tx);
    }

    /**
     * If the transactions outputs are all marked as spent, and it's in the unspent map, move it.
     * If the owned transactions outputs are not all marked as spent, and it's in the spent map, move it.
     */
    private void maybeMovePool(Transaction tx) {
        lock.lock();
        try {
            log.info("maybeMovePool {} tx {} {}", tx.isEveryOwnedOutputSpent(this) ? WalletTransaction.Pool.SPENT : WalletTransaction.Pool.UNSPENT,
                    tx.getHash(), tx.getConfidence().getConfidenceType());
            if (tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                // Transaction is confirmed, move it
                if (pending.remove(tx.getHash()) != null) {
                    if (tx.isEveryOwnedOutputSpent(this)) {
                        if (log.isInfoEnabled()) log.info("  {} <-pending ->spent", tx.getHash());
                        spent.put(tx.getHash(), tx);
                    } else {
                        if (log.isInfoEnabled()) log.info("  {} <-pending ->unspent", tx.getHash());
                        unspent.put(tx.getHash(), tx);
                    }
                } else {
                    maybeFlipSpentUnspent(tx);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Will flip transaction from spent/unspent pool if needed.
     */
    private void maybeFlipSpentUnspent(Transaction tx) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        if (tx.isEveryOwnedOutputSpent(this)) {
            // There's nothing left I can spend in this transaction.
            if (unspent.remove(tx.getHash()) != null) {
                if (log.isInfoEnabled()) log.info("  {} <-unspent ->spent", tx.getHash());
                spent.put(tx.getHash(), tx);
            }
        } else {
            if (spent.remove(tx.getHash()) != null) {
                if (log.isInfoEnabled()) log.info("  {} <-spent ->unspent", tx.getHash());
                unspent.put(tx.getHash(), tx);
            }
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

    @VisibleForTesting
    void addNewTransactionIfNeeded(Transaction tx) {
        lock.lock();
        try {
            // If was fetching this tx, remove it
            fetchingTransactions.remove(tx.getHash());

            // This tx not in wallet, add it
            if (getTransaction(tx.getHash()) == null) {
                tx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
                addWalletTransaction(WalletTransaction.Pool.PENDING, tx, true);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onTransactionUpdate(Transaction tx) {
        if (log.isInfoEnabled()) log.info("Got a new transaction {}", tx.getHash());
        lock.lock();
        try {
            addNewTransactionIfNeeded(tx);
            tryToApplyState();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void onTransactionBroadcast(Transaction tx) {
        lock.lock();
        try {
            log.info("Transaction sent {}", tx);
            //FIXME, when enabled it breaks the transactions connections and we get an incorrect coin balance
            addNewTransactionIfNeeded(tx);
        } finally {
            lock.unlock();
        }
        queueOnTransactionBroadcastSuccess(tx);
    }

    @Override
    public void onTransactionBroadcastError(Transaction tx) {
        queueOnTransactionBroadcastFailure(tx);
    }

    @Override
    public void onConnection(BlockchainConnection blockchainConnection) {
        this.blockchainConnection = blockchainConnection;
        clearTransientState();
        subscribeToBlockchain();
        subscribeIfNeeded();
        queueOnConnectivity();
    }

    @Override
    public void onDisconnect() {
        blockchainConnection = null;
        clearTransientState();
        queueOnConnectivity();
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

    void subscribeIfNeeded() {
        lock.lock();
        try {
            if (blockchainConnection != null) {
                List<Address> addressesToWatch = getAddressesToWatch();
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

    private void clearTransientState() {
        addressesSubscribed.clear();
        addressesPendingSubscription.clear();
        statusPendingUpdates.clear();
        fetchingTransactions.clear();
    }

    public void restoreWalletTransactions(ArrayList<WalletTransaction> wtxs) {
        // FIXME There is a very rare bug that doesn't properly persist tx connections, so do a sanity check by reconnecting transactions
        lock.lock();
        try {
            for (WalletTransaction wtx : wtxs) {
                simpleAddTransaction(wtx.getPool(), wtx.getTransaction());
                markNotOwnOutputs(wtx.getTransaction());
            }
            for (Transaction utx : getTransactions(false)) {
                connectTransaction(utx);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactionPool(WalletTransaction.Pool pool) {
        lock.lock();
        try {
            switch (pool) {
                case UNSPENT:
                    return unspent;
                case SPENT:
                    return spent;
                case PENDING:
                    return pending;
                case DEAD:
                    return dead;
                default:
                    throw new RuntimeException("Unknown wallet transaction type " + pool);
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
                    registration.listener.onWalletChanged(TransactionWatcherWallet.this);
                }
            });
        }
    }

    void queueOnNewBlock() {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        for (final ListenerRegistration<WalletAccountEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onNewBlock(TransactionWatcherWallet.this);
                    registration.listener.onWalletChanged(TransactionWatcherWallet.this);
                }
            });
        }
    }

    void queueOnConnectivity() {
        final WalletPocketConnectivity connectivity = getConnectivityStatus();
        for (final ListenerRegistration<WalletAccountEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onConnectivityStatus(connectivity);
                    registration.listener.onWalletChanged(TransactionWatcherWallet.this);
                }
            });
        }
    }

    void queueOnTransactionBroadcastSuccess(final Transaction tx) {
        for (final ListenerRegistration<WalletAccountEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onTransactionBroadcastSuccess(TransactionWatcherWallet.this, tx);
                }
            });
        }
    }

    void queueOnTransactionBroadcastFailure(final Transaction tx) {
        for (final ListenerRegistration<WalletAccountEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onTransactionBroadcastFailure(TransactionWatcherWallet.this, tx);
                }
            });
        }
    }

    public void addEventListener(WalletAccountEventListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
    }

    public void addEventListener(WalletAccountEventListener listener, Executor executor) {
        listeners.add(new ListenerRegistration<>(listener, executor));
    }

    public boolean removeEventListener(WalletAccountEventListener listener) {
        return ListenerRegistration.removeFromList(listener, listeners);
    }

    public boolean isLoading() {
        return !addressesPendingSubscription.isEmpty() || !statusPendingUpdates.isEmpty() || !fetchingTransactions.isEmpty();
    }


    public boolean broadcastTxSync(Transaction tx) throws IOException {
        if (isConnected()) {
            if (log.isInfoEnabled()) {
                log.info("Broadcasting tx {}", Utils.HEX.encode(tx.bitcoinSerialize()));
            }
            boolean success = blockchainConnection.broadcastTxSync(tx);
            if (success) {
                onTransactionBroadcast(tx);
            } else {
                onTransactionBroadcastError(tx);
            }
            return success;
        } else {
            throw new IOException("No connection available");
        }
    }

    public void broadcastTx(Transaction tx) throws IOException {
        broadcastTx(tx, this);
    }

    private void broadcastTx(Transaction tx, TransactionEventListener listener) throws IOException {
        if (isConnected()) {
            if (log.isInfoEnabled()) {
                log.info("Broadcasting tx {}", Utils.HEX.encode(tx.bitcoinSerialize()));
            }
            blockchainConnection.broadcastTx(tx, listener != null ? listener : this);
        } else {
            throw new IOException("No connection available");
        }
    }

    public boolean isConnected() {
        return blockchainConnection != null;
    }

    public WalletPocketConnectivity getConnectivityStatus() {
        if (!isConnected()) {
            return WalletPocketConnectivity.DISCONNECTED;
        } else {
            if (isLoading()) {
                // TODO support LOADING state, for now is just CONNECTED
                return WalletPocketConnectivity.CONNECTED;
            } else {
                return WalletPocketConnectivity.CONNECTED;
            }
        }
    }

    @Override
    public Map<Sha256Hash, Transaction> getUnspentTransactions() {
        return unspent;
    }

    @Override
    public Map<Sha256Hash, Transaction> getPendingTransactions() {
        return pending;
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactions() {
        return transactions;
    }
}
