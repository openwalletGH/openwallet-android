package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exceptions.TransactionBroadcastException;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.BlockHeader;
import com.coinomi.core.network.ServerClient;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.wallet.families.bitcoin.BitTransaction;
import com.coinomi.core.wallet.families.bitcoin.BitWalletTransaction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBag;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.UNKNOWN;

/**
 * @author John L. Jegutanis
 */
abstract public class TransactionWatcherWallet extends AbstractWallet
        implements TransactionBag, TransactionEventListener<BitTransaction> {
    private static final Logger log = LoggerFactory.getLogger(TransactionWatcherWallet.class);

    private final static int TX_DEPTH_SAVE_THRESHOLD = 4;

    @Nullable private Sha256Hash lastBlockSeenHash;
    private int lastBlockSeenHeight = -1;
    private long lastBlockSeenTimeSecs = 0;

    @VisibleForTesting
    final HashMap<TransactionOutPoint, TransactionOutput> unspentOutputs;

    // Holds the status of every address we are watching. When connecting to the server, if we get a
    // different status for a particular address this means that there are new transactions for that
    // address and we have to fetch them. The status String could be null when an address is unused.
    @VisibleForTesting
    final HashMap<AbstractAddress, String> addressesStatus;

    @VisibleForTesting final transient ArrayList<AbstractAddress> addressesSubscribed;
    @VisibleForTesting final transient ArrayList<AbstractAddress> addressesPendingSubscription;
    @VisibleForTesting final transient HashMap<AbstractAddress, AddressStatus> statusPendingUpdates;
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

    @VisibleForTesting final Map<Sha256Hash, BitTransaction> pending;
    @VisibleForTesting final Map<Sha256Hash, BitTransaction> confirmed;

    // All transactions together.
    protected final Map<Sha256Hash, BitTransaction> rawtransactions;
    private BlockchainConnection<BitTransaction> blockchainConnection;
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
    public TransactionWatcherWallet(CoinType coinType, String id) {
        super(coinType, id);
        unspentOutputs = new HashMap<>();
        addressesStatus = new HashMap<>();
        addressesSubscribed = new ArrayList<>();
        addressesPendingSubscription = new ArrayList<>();
        statusPendingUpdates = new HashMap<>();
        fetchingTransactions = new HashSet<>();
        confirmed = new HashMap<>();
        pending = new HashMap<>();
        rawtransactions = new HashMap<>();
        listeners = new CopyOnWriteArrayList<>();
    }


    @Override
    public CoinType getCoinType() {
        return type;
    }

    @Override
    public boolean isNew() {
        return rawtransactions.size() == 0;
    }

    @Override
    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    @Override
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
     */
//    public Set<Transaction> getTransactions() {
//        lock.lock();
//        try {
//            Set<Transaction> all = new HashSet<Transaction>();
//            all.addAll(confirmed.values());
//            all.addAll(pending.values());
//            return all;
//        } finally {
//            lock.unlock();
//        }
//    }

    /**
     * Returns a set of all WalletTransactions in the wallet.
     */
    public Iterable<BitWalletTransaction> getWalletTransactions() {
        lock.lock();
        try {
            Set<BitWalletTransaction> all = new HashSet<>();
            addWalletTransactionsToSet(all, WalletTransaction.Pool.CONFIRMED, confirmed.values());
            addWalletTransactionsToSet(all, WalletTransaction.Pool.PENDING, pending.values());
            return all;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Just adds the transaction to a pool without doing anything else
     */
    private void simpleAddTransaction(WalletTransaction.Pool pool, BitTransaction tx) {
        lock.lock();
        try {
//            guessTransactionSource(tx);
            rawtransactions.put(tx.getHash(), tx);
            switch (pool) {
                case CONFIRMED:
                    checkState(confirmed.put(tx.getHash(), tx) == null);
                    break;
                case PENDING:
                    checkState(pending.put(tx.getHash(), tx) == null);
                    break;
                default:
                    throw new RuntimeException("Unknown wallet transaction type " + pool);
            }
        } finally {
            lock.unlock();
        }
    }

    private static void addWalletTransactionsToSet(Set<BitWalletTransaction> txs,
                                                   WalletTransaction.Pool poolType, Collection<BitTransaction> pool) {
        for (BitTransaction tx : pool) {
            txs.add(new BitWalletTransaction(poolType, tx));
        }
    }

    /**
     * Adds a transaction that has been associated with a particular wallet pool. This is intended for usage by
     * deserialization code, such as the {@link WalletPocketProtobufSerializer} class. It isn't normally useful for
     * applications. It does not trigger auto saving.
     */
    public void addWalletTransaction(BitWalletTransaction wtx) {
        lock.lock();
        try {
            addWalletTransaction(wtx.getPool(), wtx.getTransaction(), true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove irrelevant inputs and outputs
     */
    private BitTransaction getTrimmedTransaction(Transaction txFull) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");

        Transaction tx = new Transaction(type);
        tx.setTime(txFull.getTime());
        tx.setTokenId(txFull.getTokenId());
        tx.setExtraBytes(txFull.getExtraBytes());
        tx.setUpdateTime(txFull.getUpdateTime());
        tx.setLockTime(txFull.getLockTime());

        if (txFull.getAppearsInHashes() != null) {
            for (Map.Entry<Sha256Hash, Integer> appears : txFull.getAppearsInHashes().entrySet()) {
                tx.addBlockAppearance(appears.getKey(), appears.getValue());
            }
        }

        tx.setPurpose(txFull.getPurpose());

        // Strip signatures to save space
        for (TransactionInput input : txFull.getInputs()) {
            TransactionInput noSigInput = new TransactionInput(type, null, null,
                    input.getOutpoint(), input.getValue());
            tx.addInput(noSigInput);
        }

        // Merge unrelated outputs when receiving coins
        Coin value = txFull.getValue(this);
        boolean isReceiving = value.signum() > 0;
        if (isReceiving) {
            Coin outValue = Coin.ZERO;
            for (TransactionOutput output : txFull.getOutputs()) {
                boolean isOutputMine = false;
                try {
                    isOutputMine = findKeyFromPubHash(output.getScriptPubKey().getPubKeyHash()) != null;
                } catch (ScriptException ignore) {
                    // If we don't understand this output, ignore it
                }
                if (isOutputMine) {
                    tx.addOutput(output);
                } else {
                    outValue = outValue.add(output.getValue());
                }
            }
            tx.addOutput(new TransactionOutput(type, null, outValue, new byte[0]));
        } else {
            // When sending keep all outputs
            for (TransactionOutput output : txFull.getOutputs()) {
                tx.addOutput(output);
            }
        }

        return new BitTransaction(txFull.getHash(), tx, true);
    }

    /**
     * Marks outputs as spent, if we don't have the keys
     */
    private void markNotOwnOutputs(BitTransaction transaction) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        for (TransactionOutput txo : transaction.getRawTransaction().getOutputs()) {
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
    private void addWalletTransaction(WalletTransaction.Pool pool, BitTransaction tx, boolean save) {
        lock.lock();
        try {
//            if (log.isInfoEnabled()) {
//                log.info("Adding {} tx to {} pool ({})",
//                        tx.isEveryOwnedOutputSpent(this) ? WalletTransaction.Pool.SPENT : WalletTransaction.Pool.UNSPENT, pool, tx.getHash());
//            }

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
    public Transaction getRawTransaction(Sha256Hash hash) {
        lock.lock();
        try {
            BitTransaction tx = rawtransactions.get(hash);
            if (tx != null) {
                return tx.getRawTransaction();
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns transactions that match the hashes, some transactions could be missing.
     */
    public HashMap<Sha256Hash, BitTransaction> getTransactions(HashSet<Sha256Hash> hashes) {
        lock.lock();
        try {
            HashMap<Sha256Hash, BitTransaction> txs = new HashMap<>();
            for (Sha256Hash hash : hashes) {
                if (rawtransactions.containsKey(hash)) {
                    txs.put(hash, rawtransactions.get(hash));
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
            log.info("Refreshing wallet pocket {}", type);
            lastBlockSeenHash = null;
            lastBlockSeenHeight = -1;
            lastBlockSeenTimeSecs = 0;
            unspentOutputs.clear();
            confirmed.clear();
            pending.clear();
            rawtransactions.clear();
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
            Value value = type.value(0);
            for (TransactionOutput output : unspentOutputs.values()) {
                value = value.add(output.getValue());
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

//    Value getTxBalance(Iterable<Transaction> txs, boolean toMe) {
//        lock.lock();
//        try {
//            Value value = type.value(0);
//            for (Transaction tx : txs) {
//                if (toMe) {
//                    value = value.add(tx.getValueSentToMe(this, false));
//                } else {
//                    value = value.add(tx.getValue(this));
//                }
//            }
//            return value;
//        } finally {
//            lock.unlock();
//        }
//    }

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
            AbstractAddress address = addressStatus.getAddress();
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
    public AddressStatus getAddressStatus(AbstractAddress address) {
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
            for (Map.Entry<AbstractAddress, String> status : addressesStatus.entrySet()) {
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

    private void confirmAddressSubscription(AbstractAddress address) {
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
        log.info("Got a {} block: {}", type.getName(), header.getBlockHeight());
        boolean shouldSave = false;
        lock.lock();
        try {
            lastBlockSeenTimeSecs = header.getTimestamp();
            lastBlockSeenHeight = header.getBlockHeight();
            for (BitTransaction tx : rawtransactions.values()) {
                // Save wallet when we have new TXs
                if (tx.getDepthInBlocks() < TX_DEPTH_SAVE_THRESHOLD) shouldSave = true;
                maybeUpdateBlockDepth(tx);
            }
            queueOnNewBlock();
        } finally {
            lock.unlock();
        }
        if (shouldSave) walletSaveLater();
    }

    private void maybeUpdateBlockDepth(BitTransaction tx) {

//        TransactionConfidence confidence;
        if (tx.getConfidenceType() != BUILDING) return;
        int newDepth = lastBlockSeenHeight - tx.getAppearedAtChainHeight() + 1;
        if (newDepth > 1) tx.setDepthInBlocks(newDepth);
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
                HashMap<Sha256Hash, BitTransaction> txs = getTransactions(txHashes);
                // We have all the transactions, apply state
                if (txs.size() == txHashes.size()) {
                    applyState(status, txs);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void applyState(AddressStatus status, HashMap<Sha256Hash, BitTransaction> txs) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
//        log.info("Applying state {} - {}", status.getAddress(), status.getStatus());
        // Connect inputs to outputs
        for (ServerClient.HistoryTx historyTx : status.getHistoryTxs()) {
            BitTransaction tx = txs.get(historyTx.getTxHash());
            if (tx != null) {
//                log.info("{} getHeight() = " + historyTx.getHeight(), historyTx.getTxHash());
                if (historyTx.getHeight() > 0 && tx.getDepthInBlocks() == 0) {
                    tx.setAppearedAtChainHeight(historyTx.getHeight());
                    maybeUpdateBlockDepth(tx);
                    maybeMovePool(tx);
                }
            } else {
                log.error("Could not find {} in the transactions pool. Aborting applying state",
                        historyTx.getTxHash());
                return;
            }
        }

        for (BitTransaction tx : txs.values()) {
            connectTransaction(tx);
        }

        commitAddressStatus(status);
        queueOnNewBalance();
    }

    /**
     * Mark as spent outputs that the provided inputs spend
     */
    @VisibleForTesting
    void connectTransaction(BitTransaction tx) {
        lock.lock();
        try {
            boolean isConfirmed = tx.getConfidenceType() == BUILDING;
            boolean isReceiving = tx.getValue(this).isPositive();

            // Skip if not confirmed and receiving funds
            if (!isConfirmed && isReceiving) return;

            for (TransactionInput txi : tx.getInputs()) {
                TransactionOutPoint outpoint = txi.getOutpoint();

                BitTransaction fromTx = rawtransactions.get(outpoint.getHash());
                if (fromTx != null) {
                    TransactionOutput outputToBeSpent = fromTx.getOutput((int) outpoint.getIndex());
                    if (outputToBeSpent.isAvailableForSpending()) {
                        outputToBeSpent.markAsSpent(null);
                    }
                }
                unspentOutputs.remove(outpoint);
            }

            // Add new unspent outputs
            List<TransactionOutput> outputs = tx.getOutputs();
            for (int i = 0; i < outputs.size(); i++) {
                TransactionOutput txo = outputs.get(i);
                if (txo.isAvailableForSpending() && txo.isMineOrWatched(this)) {
                    unspentOutputs.put(new TransactionOutPoint(type, i, tx.getHash()), txo);
                }
            }
        } finally {
            lock.unlock();
        }





//        if (!isConfirmed && isReceiving) return;
//        for (TransactionInput txi : tx.getInputs()) {
//
//            // todo remove start
//            TransactionOutput output = txi.getConnectedOutput();
//            if (output != null && !output.isAvailableForSpending()) {
//                // Check if the current input spends this output
//                if (output.getSpentBy() == null || output.getSpentBy().equals(txi)) {
//                    continue; // skip connected inputs
//                }
//            }
//            // todo remove end
//
//            TransactionOutPoint outpoint = txi.getOutpoint();
//            Transaction fromTx = rawtransactions.get(outpoint.getHash());
//            if (fromTx != null) {
////                TransactionOutput outputToBeSpent = fromTx.getOutput((int) outpoint.getIndex());
////                if (outputToBeSpent.isAvailableForSpending()) {
////                    outputToBeSpent.markAsSpent(null);
////                }
//
//                // todo remove start
//                // Try to connect and recover if failed once.
//                for (int i = 2; i > 0; i--) {
//                    TransactionInput.ConnectionResult result = txi.connect(fromTx, TransactionInput.ConnectMode.DISCONNECT_ON_CONFLICT);
//                    if (result == TransactionInput.ConnectionResult.NO_SUCH_TX) {
//                        log.error("Could not connect {} to {}", txi.getOutpoint(), fromTx.getHash());
//                    } else if (result == TransactionInput.ConnectionResult.ALREADY_SPENT) {
//                        TransactionOutput out = fromTx.getOutput((int) txi.getOutpoint().getIndex());
//                        log.warn("Already spent {}, forcing unspent and retry", out);
//                        out.markAsUnspent();
//                    } else {
//                        break; // No errors, break the loop
//                    }
//                }
//                // todo remove end
//
//                // Could become spent, maybe change pool
//                maybeMovePool(fromTx);
//            }
//        }
//
//        // Add new unspent outputs
//        List<TransactionOutput> outputs = tx.getOutputs();
//        for (int i = 0; i < outputs.size(); i++) {
//            TransactionOutput txo = outputs.get(i);
//            if (txo.isAvailableForSpending() && txo.isMineOrWatched(this)) {
//                unspentOutputs.put(new TransactionOutPoint(type, i, tx.getHash()), txo);
//            }
//        }

//        maybeMovePool(tx);
    }

    private void guessTransactionSource(BitTransaction tx) {
//        tx.getConfidence().setSource(TransactionConfidence.Source.SELF);
    }

    /**
     * If the transactions outputs are all marked as spent, and it's in the unspent map, move it.
     * If the owned transactions outputs are not all marked as spent, and it's in the spent map, move it.
     */
    private void maybeMovePool(BitTransaction tx) {
        lock.lock();
        try {
//            log.info("maybeMovePool {} tx {} {}", tx.isEveryOwnedOutputSpent(this) ? WalletTransaction.Pool.SPENT : WalletTransaction.Pool.UNSPENT,
//                    tx.getHash(), tx.getConfidence().getConfidenceType());
            if (tx.getConfidenceType() == BUILDING) {
                // Transaction is confirmed, move it
                if (pending.remove(tx.getHash()) != null) {
                    confirmed.put(tx.getHash(), tx);
                    connectTransaction(tx);
//                    if (tx.isEveryOwnedOutputSpent(this)) {
//                        if (log.isInfoEnabled()) log.info("  {} <-pending ->spent", tx.getHash());
//                        spent.put(tx.getHash(), tx);
//                    } else {
//                        if (log.isInfoEnabled()) log.info("  {} <-pending ->unspent", tx.getHash());
//                        unspent.put(tx.getHash(), tx);
//                    }
                }
//                else {
//                    maybeFlipSpentUnspent(tx);
//                }
            }
        } finally {
            lock.unlock();
        }
    }

//    /**
//     * Will flip transaction from spent/unspent pool if needed.
//     */
//    private void maybeFlipSpentUnspent(Transaction tx) {
//        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
//        if (tx.isEveryOwnedOutputSpent(this)) {
//            // There's nothing left I can spend in this transaction.
//            if (unspent.remove(tx.getHash()) != null) {
//                if (log.isInfoEnabled()) log.info("  {} <-unspent ->spent", tx.getHash());
//                spent.put(tx.getHash(), tx);
//            }
//        } else {
//            if (spent.remove(tx.getHash()) != null) {
//                if (log.isInfoEnabled()) log.info("  {} <-spent ->unspent", tx.getHash());
//                unspent.put(tx.getHash(), tx);
//            }
//        }
//    }


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
        return rawtransactions.containsKey(txHash) || fetchingTransactions.contains(txHash);
    }

    @VisibleForTesting
    void addNewTransactionIfNeeded(BitTransaction tx) {
        lock.lock();
        try {
            // If was fetching this tx, remove it
            fetchingTransactions.remove(tx.getHash());

            // This tx not in wallet, add it
            if (!rawtransactions.containsKey(tx.getHash())) {
                if (tx.getConfidenceType() == UNKNOWN) {
                    tx.setConfidenceType(PENDING);
                }

                WalletTransaction.Pool pool;
                switch (tx.getConfidenceType()) {
                    case BUILDING:
                        pool = WalletTransaction.Pool.CONFIRMED;
                        break;
                    case PENDING:
                        pool = WalletTransaction.Pool.PENDING;
                        break;
                    case DEAD:
                    case UNKNOWN:
                    default:
                        throw new RuntimeException("Unsupported confidence type: " +
                                tx.getConfidenceType().name());
                }
                addWalletTransaction(pool, tx, true);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onTransactionUpdate(BitTransaction tx) {
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
    public void onTransactionBroadcast(BitTransaction tx) {
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
    public void onTransactionBroadcastError(BitTransaction tx) {
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
                BitTransaction tx = (BitTransaction) wtx.getTransaction();
                simpleAddTransaction(wtx.getPool(), tx);
                markNotOwnOutputs(tx);
            }
            for (BitTransaction utx : rawtransactions.values()) {
                connectTransaction(utx);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactionPool(org.bitcoinj.wallet.WalletTransaction.Pool pool) {
        lock.lock();
        try {
            switch (pool) {
                case UNSPENT:
                case SPENT:
                    return toRawTransactions(confirmed);
                case PENDING:
                    return toRawTransactions(pending);
                case DEAD:
                default:
                    throw new RuntimeException("Unknown wallet transaction type " + pool);
            }
        } finally {
            lock.unlock();
        }
    }

    static Map<Sha256Hash, Transaction> toRawTransactions(Map<Sha256Hash, BitTransaction> txs) {
        Map<Sha256Hash, Transaction> rawTxs = new HashMap<>(txs.size());
        for (Map.Entry<Sha256Hash, BitTransaction> tx : txs.entrySet()) {
            rawTxs.put(tx.getKey(), tx.getValue().getRawTransaction());
        }
        return rawTxs;
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

    void queueOnTransactionBroadcastSuccess(final BitTransaction tx) {
        for (final ListenerRegistration<WalletAccountEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onTransactionBroadcastSuccess(TransactionWatcherWallet.this, tx);
                }
            });
        }
    }

    void queueOnTransactionBroadcastFailure(final BitTransaction tx) {
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

    @Override
    public void broadcastTx(AbstractTransaction tx) throws TransactionBroadcastException {
        if (tx instanceof BitTransaction) {
            // TODO throw transaction broadcast exception
            broadcastTx((BitTransaction) tx, this);
        } else {
            throw new TransactionBroadcastException("Incompatible transaction type: " + tx.getClass().getName());
        }
    }

    @Override
    public boolean broadcastTxSync(AbstractTransaction tx) throws TransactionBroadcastException {
        if (tx instanceof BitTransaction) {
            return broadcastBitTxSync((BitTransaction) tx);
        } else {
            throw new TransactionBroadcastException("Unsupported transaction class: " +
                    tx.getClass().getName() + ", need: " + BitTransaction.class.getName());
        }
    }

    public boolean broadcastBitTxSync(BitTransaction tx) throws TransactionBroadcastException {
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
            throw new TransactionBroadcastException("No connection available");
        }
    }

    private void broadcastTx(BitTransaction tx, TransactionEventListener<BitTransaction> listener)
            throws TransactionBroadcastException {
        if (isConnected()) {
            if (log.isInfoEnabled()) {
                log.info("Broadcasting tx {}", Utils.HEX.encode(tx.bitcoinSerialize()));
            }
            blockchainConnection.broadcastTx(tx, listener != null ? listener : this);
        } else {
            throw new TransactionBroadcastException("No connection available");
        }
    }

    public boolean isConnected() {
        return blockchainConnection != null;
    }

//    public Map<Sha256Hash, Transaction> getUnspentTransactions() {
//        return unspent;
//    }

    public Map<TransactionOutPoint, TransactionOutput> getUnspentOutputs() {
        return unspentOutputs;
    }

//    public Map<Sha256Hash, Transaction> getPendingRawTransactions() {
//        return pending;
//    }

//    public Map<Sha256Hash, Transaction> getRawTransactions() {
//        return rawtransactions;
//    }

    @Nullable
    @Override
    public AbstractTransaction getTransaction(String transactionId) {
        lock.lock();
        try {
            return rawtransactions.get(new Sha256Hash(transactionId));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<Sha256Hash, AbstractTransaction> getTransactions() {
        lock.lock();
        try {
            return toAbstractTransactions(rawtransactions);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<Sha256Hash, AbstractTransaction> getPendingTransactions() {
        lock.lock();
        try {
            return toAbstractTransactions(pending);
        } finally {
            lock.unlock();
        }
    }

    static Map<Sha256Hash, AbstractTransaction> toAbstractTransactions(Map<Sha256Hash, BitTransaction> txMap) {
        Map<Sha256Hash, AbstractTransaction> txs = new HashMap<>();
        for (Map.Entry<Sha256Hash, BitTransaction> tx : txMap.entrySet()) {
            txs.put(tx.getKey(), tx.getValue());
        }
        return txs;
    }
}
