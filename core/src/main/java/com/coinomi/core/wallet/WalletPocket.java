/**
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2014 Giannis Dzegoutanis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.ServerClient.HistoryTx;
import com.coinomi.core.network.ServerClient.UnspentTx;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.protos.Protos;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionBag;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionInput.ConnectMode;
import com.google.bitcoin.core.TransactionInput.ConnectionResult;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.VarInt;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.KeyCrypter;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.signers.LocalTransactionSigner;
import com.google.bitcoin.signers.MissingSigResolutionSigner;
import com.google.bitcoin.signers.TransactionSigner;
import com.google.bitcoin.utils.ListenerRegistration;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.wallet.CoinSelection;
import com.google.bitcoin.wallet.CoinSelector;
import com.google.bitcoin.wallet.DecryptingKeyBag;
import com.google.bitcoin.wallet.DefaultCoinSelector;
import com.google.bitcoin.wallet.KeyBag;
import com.google.bitcoin.wallet.RedeemData;
import com.google.bitcoin.wallet.WalletTransaction;
import com.google.bitcoin.wallet.WalletTransaction.Pool;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkArgument;
import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * @author Giannis Dzegoutanis
 *
 *
 */
public class WalletPocket implements TransactionBag, TransactionEventListener, ConnectionEventListener, Serializable {
    private static final Logger log = LoggerFactory.getLogger(WalletPocket.class);
    private final ReentrantLock lock = Threading.lock("WalletPocket");

    private final CoinType coinType;

    private String description;

    @Nullable private Sha256Hash lastBlockSeenHash;
    private int lastBlockSeenHeight = -1;
    private long lastBlockSeenTimeSecs = 0;

    // Holds the status of every address we are watching. When connecting to the server, if we get a
    // different status for a particular address this means that there are new transactions for that
    // address and we have to fetch them. The status String could be null when an address is unused.
    @VisibleForTesting final HashMap<Address, String> addressesStatus;

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

    public @VisibleForTesting SimpleHDKeyChain keys;

    @Nullable private transient BlockchainConnection blockchainConnection;

    protected transient CoinSelector coinSelector = new DefaultCoinSelector();

    private final transient CopyOnWriteArrayList<ListenerRegistration<WalletPocketEventListener>> listeners;
    @Nullable private transient Wallet wallet = null;

    public WalletPocket(DeterministicKey rootKey, CoinType coinType,
                        @Nullable KeyCrypter keyCrypter, @Nullable KeyParameter key) {
        this(new SimpleHDKeyChain(rootKey, keyCrypter, key), coinType);
    }

    WalletPocket(SimpleHDKeyChain keys, CoinType coinType) {
        this.keys = checkNotNull(keys);
        this.coinType = checkNotNull(coinType);
        int lookAhead = 2 * SimpleHDKeyChain.LOOKAHEAD;
        addressesStatus = new HashMap<Address, String>(lookAhead);
        addressesSubscribed = new ArrayList<Address>(lookAhead);
        addressesPendingSubscription = new ArrayList<Address>(lookAhead);
        statusPendingUpdates = new HashMap<Address, AddressStatus>(lookAhead);
        fetchingTransactions = new HashSet<Sha256Hash>();
        unspent = new HashMap<Sha256Hash, Transaction>();
        spent = new HashMap<Sha256Hash, Transaction>();
        pending = new HashMap<Sha256Hash, Transaction>();
        dead = new HashMap<Sha256Hash, Transaction>();
        transactions = new HashMap<Sha256Hash, Transaction>();
        listeners = new CopyOnWriteArrayList<ListenerRegistration<WalletPocketEventListener>>();
    }


    /******************************************************************************************************************/

    //region Vending transactions and other internal state

    public boolean isNew() {
        return unspent.size() + spent.size() + pending.size() == 0;
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
            addWalletTransactionsToSet(all, Pool.UNSPENT, unspent.values());
            addWalletTransactionsToSet(all, Pool.SPENT, spent.values());
            addWalletTransactionsToSet(all, Pool.DEAD, dead.values());
            addWalletTransactionsToSet(all, Pool.PENDING, pending.values());
            return all;
        } finally {
            lock.unlock();
        }
    }

    private static void addWalletTransactionsToSet(Set<WalletTransaction> txs,
                                                   Pool poolType, Collection<Transaction> pool) {
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
        checkState(lock.isHeldByCurrentThread());
        for (TransactionOutput txo : transaction.getOutputs()) {
            if (txo.isAvailableForSpending()) {
                // We don't have keys for this txo therefore it is not ours
                if (keys.findKeyFromPubHash(txo.getScriptPubKey().getPubKeyHash()) == null) {
                    txo.markAsSpent(null);
                }
            }
        }
    }

    /**
     * Adds the given transaction to the given pools and registers a confidence change listener on it.
     */
    private void addWalletTransaction(Pool pool, Transaction tx, boolean save) {
        lock.lock();
        try {
            if (log.isInfoEnabled()) {
                log.info("Adding {} tx {} to {}",
                        tx.isEveryOwnedOutputSpent(this) ? Pool.SPENT : Pool.UNSPENT, tx.getHash(), pool);
                if (!tx.isEveryOwnedOutputSpent(this)) {
                    for (TransactionOutput transactionOutput : tx.getOutputs()) {
                        log.info("{} txo index {}",
                                transactionOutput.isAvailableForSpending() ? Pool.UNSPENT : Pool.SPENT,
                                transactionOutput.getIndex());
                    }
                }
            }

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
    public void refresh() {
        lock.lock();
        try {
            log.info("Refreshing wallet pocket {}", coinType);
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

    public CoinType getCoinType() {
        return coinType;
    }

    /**
     * Get the wallet pocket's KeyCrypter, or null if the wallet pocket is not encrypted.
     * (Used in encrypting/ decrypting an ECKey).
     */
    @Nullable
    public KeyCrypter getKeyCrypter() {
        return keys.getKeyCrypter();
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
     * Returns a {@link Date} representing the time extracted from the last best seen block header. This timestamp
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

    /**
     * Set the description of the wallet.
     * This is a Unicode encoding string typically entered by the user as descriptive text for the wallet.
     */
    public void setDescription(String description) {
        lock.lock();
        this.description = description;
        lock.unlock();
        walletSaveNow();
    }

    /**
     * Get the description of the wallet. See {@link WalletPocket#setDescription(String))}
     */
    public String getDescription() {
        return description;
    }

    public Coin getBalance() {
        return getBalance(false);
    }

    public Coin getBalance(boolean includeUnconfirmed) {
        lock.lock();
        try {
//            log.info("Get balance includeUnconfirmed = {}", includeUnconfirmed);
            if (includeUnconfirmed) {
                return getTxBalance(Iterables.concat(unspent.values(), pending.values()), true);
            }
            return getTxBalance(unspent.values(), true);
        } finally {
            lock.unlock();
        }
    }


    public Coin getPendingBalance() {
        lock.lock();
        try {
//            log.info("Get pending balance");
            return getTxBalance(pending.values(), false);
        } finally {
            lock.unlock();
        }
    }

    Coin getTxBalance(Iterable<Transaction> txs, boolean toMe) {
        lock.lock();
        try {
            Coin value = Coin.ZERO;
            for (Transaction tx : txs) {
//                log.info("tx {}", tx.getHash());
//                log.info("tx.getValue {}", tx.getValue(this));
//                log.info("tx.getValueSentToMe {}", tx.getValueSentToMe(this));
//                log.info("tx.getValueSentFromMe {}", tx.getValueSentFromMe(this));

//                log.info("Balance for tx {}", tx.getHash());
//                for (TransactionOutput txo : tx.getOutputs()) {
//                    log.info("|- {} txo {} value {}",
//                            txo.isAvailableForSpending() ? Pool.UNSPENT : Pool.SPENT,
//                            txo.getIndex(), txo.getValue());
//                }

                if (toMe) {
                    value = value.add(tx.getValueSentToMe(this));
                } else {
                    value = value.add(tx.getValue(this));
                }
            }
            return value;
        } finally {
            lock.unlock();
        }
    }


    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
    }


    public void broadcastTx(Transaction tx, TransactionEventListener listener) throws IOException {
        if (isConnected()) {
            blockchainConnection.broadcastTx(tx, listener);
        } else {
            throw new IOException("No connection available");
        }
    }

    public boolean isConnected() {
        return blockchainConnection != null;
    }

    //endregion

    /**
     * **************************************************************************************************************
     */

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
        for (ECKey key : keys.getLeafKeys()) {
            Address address = key.toAddress(coinType);
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
                log.info("Subscribed to {}", address);
                addressesPendingSubscription.remove(address);
                addressesSubscribed.add(address);
            }
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void onAddressStatusUpdate(AddressStatus status) {
        log.info("Got new status {}", status);
        lock.lock();
        try {
            confirmAddressSubscription(status.getAddress());
            if (status.getStatus() != null) {
                keys.markPubHashAsUsed(status.getAddress().getHash160());
                subscribeIfNeeded();

                if (isAddressStatusChanged(status)) {
                    // Status changed, time to update
                    if (registerStatusForUpdate(status)) {
                        log.info("Must get transactions for address {}, status {}",
                                status.getAddress(), status.getStatus());

                        if (blockchainConnection != null) {
                            blockchainConnection.getHistoryTx(status, this);
                            blockchainConnection.getUnspentTx(status, this);
                        }
                    }
                    else {
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
    public void onTransactionHistory(AddressStatus status, List<HistoryTx> historyTxes) {
        lock.lock();
        try {
            AddressStatus updatingStatus = statusPendingUpdates.get(status.getAddress());
            // Check if this updating status is valid
            if (updatingStatus != null && updatingStatus.equals(status)) {
                updatingStatus.queueHistoryTransactions(historyTxes);
                fetchTransactions(historyTxes);
                tryToApplyState(updatingStatus);
            }
            else {
                log.info("Ignoring history tx call because no entry found or newer entry.");
            }
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void onUnspentTransactionUpdate(AddressStatus status, List<UnspentTx> unspentTxes) {
        lock.lock();
        try {
            AddressStatus updatingStatus = statusPendingUpdates.get(status.getAddress());
            // Check if this updating status is valid
            if (updatingStatus != null && updatingStatus.equals(status)) {
                updatingStatus.queueUnspentTransactions(unspentTxes);
                fetchTransactions(unspentTxes);
                tryToApplyState(updatingStatus);
            }
            else {
                log.info("Ignoring unspent tx call because no entry found or newer entry.");
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
        checkState(lock.isHeldByCurrentThread());
        log.info("Applying state {} - {}", status.getAddress(), status.getStatus());
        // Connect inputs to outputs
        for (HistoryTx historyTx : status.getHistoryTxs()) {
            Transaction tx = txs.get(historyTx.getTxHash());
            if (tx != null) {
                log.info("{} getHeight() = " + historyTx.getHeight(), historyTx.getTxHash());
                if (historyTx.getHeight() > 0) {
                    tx.getConfidence().setAppearedAtChainHeight(historyTx.getHeight());
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

        markUnspentTXO(status, txs);

        commitAddressStatus(status);
        queueOnNewBalance();
    }

    private void markUnspentTXO(AddressStatus status, Map<Sha256Hash, Transaction> txs) {
        Set<UnspentTx> utxs = status.getUnspentTxs();
        ArrayList<TransactionOutput> unspentOutputs = new ArrayList<TransactionOutput>(utxs.size());
        // Mark unspent outputs
        for (UnspentTx utx : utxs) {
            if (txs.containsKey(utx.getTxHash())) {
                Transaction tx = txs.get(utx.getTxHash());
                TransactionOutput output = tx.getOutput(utx.getTxPos());
                if (!output.isAvailableForSpending()) output.markAsUnspent();
                unspentOutputs.add(output);
            }
        }

        for (TransactionOutput output : unspentOutputs) {
            log.info("- UΤΧO {}:{} - {}", output.getParentTransaction().getHash(),
                    output.getIndex(), output.getAddressFromP2PKHScript(coinType));
        }

        // Mark spent outputs and change pools if needed
        for (Transaction tx : txs.values()) {
            if (!tx.isEveryOwnedOutputSpent(this)) {
                log.info("tx {} is {}", tx.getHash(), tx.getConfidence().getConfidenceType());
                for (TransactionOutput txo : tx.getOutputs()) {
                    log.info("- ΤΧΟ {}:{}", txo.getParentTransaction().getHash(), txo.getIndex());

                    boolean belongsToAddress = Arrays.equals(txo.getScriptPubKey().getPubKeyHash(),
                            status.getAddress().getHash160());

                    if (belongsToAddress && !unspentOutputs.contains(txo)) {
                        // if not pending and has unspent outputs that should be spent
                        if (!isTxPending(txo.getParentTransaction()) && txo.isAvailableForSpending()) {
                            log.info("Marking TXO as spent {}:{}", txo.getParentTransaction().getHash(), txo.getIndex());
                            txo.markAsSpent(null);
                        }
                    }
                }
            }

            maybeMovePool(tx);
        }
    }

    private boolean isTxPending(Transaction tx) {
        return tx.getConfidence().getConfidenceType() == ConfidenceType.PENDING;
    }

    private void connectTransaction(Transaction tx) {
        checkState(lock.isHeldByCurrentThread());
        // Skip if not confirmed
        if (tx.getConfidence().getConfidenceType() != ConfidenceType.BUILDING) return;
        // Connect to other transactions in the wallet pocket
        if (log.isInfoEnabled()) log.info("Connecting inputs of tx {}", tx.getHash());
        for (TransactionInput txi : tx.getInputs()) {
            if (txi.getConnectedOutput() != null) continue; // skip connected inputs
            Sha256Hash outputHash = txi.getOutpoint().getHash();
            Transaction fromTx = transactions.get(outputHash);
            if (fromTx != null) {
                // Try to connect and recover if failed once.
                for (int i = 2; i > 0; i--) {
                    ConnectionResult result = txi.connect(fromTx, ConnectMode.DISCONNECT_ON_CONFLICT);
                    if (result == ConnectionResult.NO_SUCH_TX) {
                        log.error("Could not connect {} to {}", txi.getOutpoint(), fromTx.getHash());
                    } else if (result == ConnectionResult.ALREADY_SPENT) {
                        TransactionOutput out = fromTx.getOutput((int) txi.getOutpoint().getIndex());
                        log.warn("Already spent {}, forcing unspent and retry", out);
                        out.markAsUnspent();
                    } else {
                        log.info("Txi connected to {}:{}", fromTx.getHash(), txi.getOutpoint().getIndex());
                        break; // No errors, break the loop
                    }
                }
                // Could become spent, maybe change pool
                maybeMovePool(fromTx);
            }
            else {
                log.warn("No output found for input {}:{}", txi.getParentTransaction().getHash(),
                        txi.getOutpoint().getIndex());
            }
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
            log.info("maybeMovePool {} tx {} {}", tx.isEveryOwnedOutputSpent(this) ? Pool.SPENT : Pool.UNSPENT,
                    tx.getHash(), tx.getConfidence().getConfidenceType());
            if (tx.getConfidence().getConfidenceType() == ConfidenceType.BUILDING) {
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
        checkState(lock.isHeldByCurrentThread());
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


    private void fetchTransactions(List<? extends HistoryTx> txes) {
        checkState(lock.isHeldByCurrentThread());
        for (HistoryTx tx : txes) {
            fetchTransactionIfNeeded(tx.getTxHash());
        }
    }

    private void fetchTransactionIfNeeded(Sha256Hash txHash) {
        checkState(lock.isHeldByCurrentThread());
        // Check if need to fetch the transaction
        if (!isTransactionAvailableOrQueued(txHash)) {
            fetchingTransactions.add(txHash);
            if (blockchainConnection != null) {
                blockchainConnection.getTransaction(txHash, this);
            }
        }
    }

    private boolean isTransactionAvailableOrQueued(Sha256Hash txHash) {
        checkState(lock.isHeldByCurrentThread());
        return getTransaction(txHash) != null || fetchingTransactions.contains(txHash);
    }

    private void addNewTransactionIfNeeded(Transaction tx) {
        checkState(lock.isHeldByCurrentThread());

        // If was fetching this tx, remove it
        fetchingTransactions.remove(tx.getHash());

        // This tx not in wallet, add it
        if (getTransaction(tx.getHash()) == null) {
            tx.getConfidence().setConfidenceType(ConfidenceType.PENDING);
            addWalletTransaction(Pool.PENDING, tx, true);
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
//            addNewTransactionIfNeeded(tx);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onTransactionBroadcastError(Transaction tx, Throwable throwable) {
        log.error("Error broadcasting transaction {}", tx.getHash(), throwable);
    }

    @Override
    public void onConnection(BlockchainConnection blockchainConnection) {
        this.blockchainConnection = blockchainConnection;
        clearTransientState();
        subscribeIfNeeded();
    }

    @Override
    public void onDisconnect() {
        blockchainConnection = null;
        clearTransientState();
    }

    private void subscribeIfNeeded() {
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
        }
        finally {
            lock.unlock();
        }
    }

    private void clearTransientState() {
        addressesSubscribed.clear();
        addressesPendingSubscription.clear();
        statusPendingUpdates.clear();
        fetchingTransactions.clear();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Event listener support
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void addEventListener(WalletPocketEventListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
    }

    public void addEventListener(WalletPocketEventListener listener, Executor executor) {
        listeners.add(new ListenerRegistration<WalletPocketEventListener>(listener, executor));
    }

    public boolean removeEventListener(WalletPocketEventListener listener) {
        return ListenerRegistration.removeFromList(listener, listeners);
    }

    private void queueOnNewBalance() {
        checkState(lock.isHeldByCurrentThread());
        final Coin balance = getBalance();
        final Coin pendingBalance = getPendingBalance();
        for (final ListenerRegistration<WalletPocketEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onNewBalance(balance, pendingBalance);
                    registration.listener.onPocketChanged(WalletPocket.this);
                }
            });
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Serialization support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    List<Protos.Key> serializeKeychainToProtobuf() {
        lock.lock();
        try {
            return keys.toProtobuf();
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting Protos.WalletPocket toProtobuf() {
        lock.lock();
        try {
            return WalletPocketProtobufSerializer.toProtobuf(this);
        } finally {
            lock.unlock();
        }
    }

    // Util
    private void walletSaveLater() {
        if (wallet != null) {
            // Save in another thread to avoid cyclic locking of Wallet and WalletPocket
            Threading.USER_THREAD.execute(new Runnable() {
                @Override
                public void run() {
                    wallet.saveLater();
                }
            });
        }
    }

    private void walletSaveNow() {
        if (wallet != null) {
            // Save in another thread to avoid cyclic locking of Wallet and WalletPocket
            Threading.USER_THREAD.execute(new Runnable() {
                @Override
                public void run() {
                    wallet.saveNow();
                }
            });
        }
    }

    public void restoreWalletTransactions(ArrayList<WalletTransaction> wtxs) {
        for (WalletTransaction wtx : wtxs) {
            addWalletTransaction(wtx.getPool(), wtx.getTransaction(), false);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Encryption support
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    boolean isEncrypted() {
        return keys.isEncrypted();
    }

    /**
     * Encrypt the keys in the group using the KeyCrypter and the AES key. A good default KeyCrypter to use is
     * {@link com.google.bitcoin.crypto.KeyCrypterScrypt}.
     *
     * @throws com.google.bitcoin.crypto.KeyCrypterException Thrown if the wallet encryption fails for some reason,
     *         leaving the group unchanged.
     */
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        checkNotNull(keyCrypter);
        checkNotNull(aesKey);

        lock.lock();
        try {
            this.keys = this.keys.toEncrypted(keyCrypter, aesKey);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Decrypt the keys in the group using the previously given key crypter and the AES key. A good default
     * KeyCrypter to use is {@link com.google.bitcoin.crypto.KeyCrypterScrypt}.
     *
     * @throws com.google.bitcoin.crypto.KeyCrypterException Thrown if the wallet decryption fails for some reason, leaving the group unchanged.
     */
    /* package */ void decrypt(KeyParameter aesKey) {
        checkNotNull(aesKey);

        lock.lock();
        try {
            this.keys = this.keys.toDecrypted(aesKey);
        } finally {
            lock.unlock();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Transaction signing support
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sends coins to the given address but does not broadcast the resulting pending transaction.
     */
    public SendRequest sendCoinsOffline(Address address, Coin amount) throws InsufficientMoneyException {
        return sendCoinsOffline(address, amount, (KeyParameter) null);
    }

    /**
     * {@link #sendCoinsOffline(Address, Coin)}
     */
    public SendRequest sendCoinsOffline(Address address, Coin amount, @Nullable String password)
            throws InsufficientMoneyException {
        KeyParameter key = null;
        if (password != null) {
            checkState(isEncrypted());
            key = checkNotNull(getKeyCrypter()).deriveKey(password);
        }
        return sendCoinsOffline(address, amount, key);
    }


    /**
     * {@link #sendCoinsOffline(Address, Coin)}
     */
    public SendRequest sendCoinsOffline(Address address, Coin amount, @Nullable KeyParameter aesKey)
            throws InsufficientMoneyException {
        checkState(address.getParameters() instanceof CoinType);
        SendRequest request = SendRequest.to(address, amount);
        request.aesKey = aesKey;

        return request;
    }

    @Override
    public boolean isPubKeyHashMine(byte[] pubkeyHash) {
        return findKeyFromPubHash(pubkeyHash) != null;
    }

    @Override
    public boolean isWatchedScript(Script script) {
        // Not supported
        return false;
    }

    @Override
    public boolean isPubKeyMine(byte[] pubkey) {
        return findKeyFromPubKey(pubkey) != null;
    }

    @Override
    public boolean isPayToScriptHashMine(byte[] payToScriptHash) {
        // Not supported
        return false;
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactionPool(Pool pool) {
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

    private static class FeeCalculation {
        public CoinSelection bestCoinSelection;
        public TransactionOutput bestChangeOutput;
    }

    /**
     * Given a spend request containing an incomplete transaction, makes it valid by adding outputs and signed inputs
     * according to the instructions in the request. The transaction in the request is modified by this method.
     *
     * @param req a SendRequest that contains the incomplete transaction and details for how to make it valid.
     * @throws InsufficientMoneyException if the request could not be completed due to not enough balance.
     * @throws IllegalArgumentException if you try and complete the same SendRequest twice
     */
    public void completeTx(SendRequest req) throws InsufficientMoneyException {
        lock.lock();
        try {
            checkArgument(!req.completed, "Given SendRequest has already been completed.");
            // Calculate the amount of value we need to import.
            Coin value = Coin.ZERO;
            for (TransactionOutput output : req.tx.getOutputs()) {
                value = value.add(output.getValue());
            }

            log.info("Completing send tx with {} outputs totalling {} (not including fees)",
                    req.tx.getOutputs().size(), value.toFriendlyString());

            // If any inputs have already been added, we don't need to get their value from wallet
            Coin totalInput = Coin.ZERO;
            for (TransactionInput input : req.tx.getInputs())
                if (input.getConnectedOutput() != null)
                    totalInput = totalInput.add(input.getConnectedOutput().getValue());
                else
                    log.warn("SendRequest transaction already has inputs but we don't know how much they are worth - they will be added to fee.");
            value = value.subtract(totalInput);

            List<TransactionInput> originalInputs = new ArrayList<TransactionInput>(req.tx.getInputs());

            // We need to know if we need to add an additional fee because one of our values are smaller than 0.01 BTC
            boolean needAtLeastReferenceFee = false;
            if (req.ensureMinRequiredFee && !req.emptyWallet) { // min fee checking is handled later for emptyWallet
                for (TransactionOutput output : req.tx.getOutputs())
                    if (output.getValue().compareTo(Coin.CENT) < 0) {
                        if (output.getValue().compareTo(output.getMinNonDustValue(coinType.getFeePerKb().multiply(3))) < 0)
                            throw new com.google.bitcoin.core.Wallet.DustySendRequested();
                        needAtLeastReferenceFee = true;
                        break;
                    }
            }

            // Calculate a list of ALL potential candidates for spending and then ask a coin selector to provide us
            // with the actual outputs that'll be used to gather the required amount of value. In this way, users
            // can customize coin selection policies.
            //
            // Note that this code is poorly optimized: the spend candidates only alter when transactions in the wallet
            // change - it could be pre-calculated and held in RAM, and this is probably an optimization worth doing.
            LinkedList<TransactionOutput> candidates = calculateAllSpendCandidates(true);
            CoinSelection bestCoinSelection;
            TransactionOutput bestChangeOutput = null;
            if (!req.emptyWallet) {
                // This can throw InsufficientMoneyException.
                FeeCalculation feeCalculation;
                feeCalculation = calculateFee(req, value, originalInputs, needAtLeastReferenceFee, candidates);
                bestCoinSelection = feeCalculation.bestCoinSelection;
                bestChangeOutput = feeCalculation.bestChangeOutput;
            } else {
                // We're being asked to empty the wallet. What this means is ensuring "tx" has only a single output
                // of the total value we can currently spend as determined by the selector, and then subtracting the fee.
                checkState(req.tx.getOutputs().size() == 1, "Empty wallet TX must have a single output only.");
                CoinSelector selector = req.coinSelector == null ? coinSelector : req.coinSelector;
                bestCoinSelection = selector.select(NetworkParameters.MAX_MONEY, candidates);
                candidates = null;  // Selector took ownership and might have changed candidates. Don't access again.
                req.tx.getOutput(0).setValue(bestCoinSelection.valueGathered);
                log.info("  emptying {}", bestCoinSelection.valueGathered.toFriendlyString());
            }

            for (TransactionOutput output : bestCoinSelection.gathered)
                req.tx.addInput(output);

            if (req.ensureMinRequiredFee && req.emptyWallet) {
                final Coin baseFee = req.fee == null ? Coin.ZERO : req.fee;
                final Coin feePerKb = req.feePerKb == null ? Coin.ZERO : req.feePerKb;
                Transaction tx = req.tx;
                if (!adjustOutputDownwardsForFee(tx, bestCoinSelection, baseFee, feePerKb))
                    throw new com.google.bitcoin.core.Wallet.CouldNotAdjustDownwards();
            }

            if (bestChangeOutput != null) {
                req.tx.addOutput(bestChangeOutput);
                log.info("  with {} change", bestChangeOutput.getValue().toFriendlyString());
            }

            // Now shuffle the outputs to obfuscate which is the change.
            if (req.shuffleOutputs)
                req.tx.shuffleOutputs();

            // Now sign the inputs, thus proving that we are entitled to redeem the connected outputs.
            if (req.signInputs) {
                signTransaction(req);
            }

            // Check size.
            int size = req.tx.bitcoinSerialize().length;
            if (size > Transaction.MAX_STANDARD_TX_SIZE)
                throw new com.google.bitcoin.core.Wallet.ExceededMaxTransactionSize();

            final Coin calculatedFee = req.tx.getFee();
            if (calculatedFee != null) {
                log.info("  with a fee of {} BTC", calculatedFee.toFriendlyString());
            }

            // Label the transaction as being self created. We can use this later to spend its change output even before
            // the transaction is confirmed. We deliberately won't bother notifying listeners here as there's not much
            // point - the user isn't interested in a confidence transition they made themselves.
            req.tx.getConfidence().setSource(TransactionConfidence.Source.SELF);
            // Label the transaction as being a user requested payment. This can be used to render GUI wallet
            // transaction lists more appropriately, especially when the wallet starts to generate transactions itself
            // for internal purposes.
            req.tx.setPurpose(Transaction.Purpose.USER_PAYMENT);
            req.completed = true;
            req.fee = calculatedFee;
            log.info("  completed: {}", req.tx);
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>Given a send request containing transaction, attempts to sign it's inputs. This method expects transaction
     * to have all necessary inputs connected or they will be ignored.</p>
     * <p>Actual signing is done by pluggable {@link com.google.bitcoin.signers.LocalTransactionSigner}
     * and it's not guaranteed that transaction will be complete in the end.</p>
     */
    public void signTransaction(SendRequest req) {
        lock.lock();
        try {
            Transaction tx = req.tx;
            List<TransactionInput> inputs = tx.getInputs();
            List<TransactionOutput> outputs = tx.getOutputs();
            checkState(inputs.size() > 0);
            checkState(outputs.size() > 0);

            KeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(keys, req.aesKey);

            int numInputs = tx.getInputs().size();
            for (int i = 0; i < numInputs; i++) {
                TransactionInput txIn = tx.getInput(i);
                if (txIn.getConnectedOutput() == null) {
                    log.warn("Missing connected output, assuming input {} is already signed.", i);
                    continue;
                }

                try {
                    // We assume if its already signed, its hopefully got a SIGHASH type that will not invalidate when
                    // we sign missing pieces (to check this would require either assuming any signatures are signing
                    // standard output types or a way to get processed signatures out of script execution)
                    txIn.getScriptSig().correctlySpends(tx, i, txIn.getConnectedOutput().getScriptPubKey());
                    log.warn("Input {} already correctly spends output, assuming SIGHASH type used will be safe and skipping signing.", i);
                    continue;
                } catch (ScriptException e) {
                    // Expected.
                }

                Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
                RedeemData redeemData = txIn.getConnectedRedeemData(maybeDecryptingKeyBag);
                checkNotNull(redeemData, "Transaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
                txIn.setScriptSig(scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript));
            }

            TransactionSigner.ProposedTransaction proposal = new TransactionSigner.ProposedTransaction(tx);
            TransactionSigner signer = new LocalTransactionSigner();
            if (!signer.signInputs(proposal, maybeDecryptingKeyBag)) {
                log.info("{} returned false for the tx", signer.getClass().getName());
            }

            // resolve missing sigs if any
            new MissingSigResolutionSigner(req.missingSigsMode).signInputs(proposal, maybeDecryptingKeyBag);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a list of all possible outputs we could possibly spend, potentially even including immature coinbases
     * (which the protocol may forbid us from spending). In other words, return all outputs that this wallet holds
     * keys for and which are not already marked as spent.
     */
    public LinkedList<TransactionOutput> calculateAllSpendCandidates(boolean excludeImmatureCoinbases) {
        lock.lock();
        try {
            LinkedList<TransactionOutput> candidates = Lists.newLinkedList();
            for (Transaction tx : Iterables.concat(unspent.values(), pending.values())) {
                // Do not try and spend coinbases that were mined too recently, the protocol forbids it.
                if (excludeImmatureCoinbases && !tx.isMature()) continue;
                for (TransactionOutput output : tx.getOutputs()) {
                    if (!output.isAvailableForSpending()) continue;
//                    if (!output.isMine(this)) continue;
                    candidates.add(output);
                }
            }
            return candidates;
        } finally {
            lock.unlock();
        }
    }

    public FeeCalculation calculateFee(SendRequest req, Coin value, List<TransactionInput> originalInputs,
                                       boolean needAtLeastReferenceFee, LinkedList<TransactionOutput> candidates) throws InsufficientMoneyException {
        checkState(lock.isHeldByCurrentThread());
        FeeCalculation result = new FeeCalculation();
        // There are 3 possibilities for what adding change might do:
        // 1) No effect
        // 2) Causes increase in fee (change < 0.01 COINS)
        // 3) Causes the transaction to have a dust output or change < fee increase (ie change will be thrown away)
        // If we get either of the last 2, we keep note of what the inputs looked like at the time and try to
        // add inputs as we go up the list (keeping track of minimum inputs for each category).  At the end, we pick
        // the best input set as the one which generates the lowest total fee.
        Coin additionalValueForNextCategory = null;
        CoinSelection selection3 = null;
        CoinSelection selection2 = null;
        TransactionOutput selection2Change = null;
        CoinSelection selection1 = null;
        TransactionOutput selection1Change = null;
        // We keep track of the last size of the transaction we calculated but only if the act of adding inputs and
        // change resulted in the size crossing a 1000 byte boundary. Otherwise it stays at zero.
        int lastCalculatedSize = 0;
        Coin valueNeeded, valueMissing = null;
        while (true) {
            resetTxInputs(req, originalInputs);

            Coin fees = req.fee == null ? Coin.ZERO : req.fee;
            if (lastCalculatedSize > 0) {
                // If the size is exactly 1000 bytes then we'll over-pay, but this should be rare.
                fees = fees.add(req.feePerKb.multiply((lastCalculatedSize / 1000) + 1));
            } else {
                fees = fees.add(req.feePerKb);  // First time around the loop.
            }
            if (needAtLeastReferenceFee && fees.compareTo(coinType.getFeePerKb()) < 0)
                fees = coinType.getFeePerKb();

            valueNeeded = value.add(fees);
            if (additionalValueForNextCategory != null)
                valueNeeded = valueNeeded.add(additionalValueForNextCategory);
            Coin additionalValueSelected = additionalValueForNextCategory;

            // Of the coins we could spend, pick some that we actually will spend.
            CoinSelector selector = req.coinSelector == null ? coinSelector : req.coinSelector;
            // selector is allowed to modify candidates list.
            CoinSelection selection = selector.select(valueNeeded, new LinkedList<TransactionOutput>(candidates));
            // Can we afford this?
            if (selection.valueGathered.compareTo(valueNeeded) < 0) {
                valueMissing = valueNeeded.subtract(selection.valueGathered);
                break;
            }
            checkState(selection.gathered.size() > 0 || originalInputs.size() > 0);

            // We keep track of an upper bound on transaction size to calculate fees that need to be added.
            // Note that the difference between the upper bound and lower bound is usually small enough that it
            // will be very rare that we pay a fee we do not need to.
            //
            // We can't be sure a selection is valid until we check fee per kb at the end, so we just store
            // them here temporarily.
            boolean eitherCategory2Or3 = false;
            boolean isCategory3 = false;

            Coin change = selection.valueGathered.subtract(valueNeeded);
            if (additionalValueSelected != null)
                change = change.add(additionalValueSelected);

            // If change is < 0.01 BTC, we will need to have at least minfee to be accepted by the network
            if (req.ensureMinRequiredFee && !change.equals(Coin.ZERO) &&
                    change.compareTo(Coin.CENT) < 0 && fees.compareTo(coinType.getFeePerKb()) < 0) {
                // This solution may fit into category 2, but it may also be category 3, we'll check that later
                eitherCategory2Or3 = true;
                additionalValueForNextCategory = Coin.CENT;
                // If the change is smaller than the fee we want to add, this will be negative
                change = change.subtract(coinType.getFeePerKb().subtract(fees));
            }

            int size = 0;
            TransactionOutput changeOutput = null;
            if (change.signum() > 0) {
                // The value of the inputs is greater than what we want to send. Just like in real life then,
                // we need to take back some coins ... this is called "change". Add another output that sends the change
                // back to us. The address comes either from the request or getChangeAddress() as a default.
                Address changeAddress = req.changeAddress;
                if (changeAddress == null)
                    changeAddress = getChangeAddress();
                changeOutput = new TransactionOutput(coinType, req.tx, change, changeAddress);
                // If the change output would result in this transaction being rejected as dust, just drop the change and make it a fee
                if (req.ensureMinRequiredFee && coinType.getMinNonDust().compareTo(change) >= 0) {
                    // This solution definitely fits in category 3
                    isCategory3 = true;
                    additionalValueForNextCategory = coinType.getFeePerKb().add(
                            coinType.getMinNonDust().add(Coin.SATOSHI));
                } else {
                    size += changeOutput.bitcoinSerialize().length + VarInt.sizeOf(req.tx.getOutputs().size()) - VarInt.sizeOf(req.tx.getOutputs().size() - 1);
                    // This solution is either category 1 or 2
                    if (!eitherCategory2Or3) // must be category 1
                        additionalValueForNextCategory = null;
                }
            } else {
                if (eitherCategory2Or3) {
                    // This solution definitely fits in category 3 (we threw away change because it was smaller than MIN_TX_FEE)
                    isCategory3 = true;
                    additionalValueForNextCategory = coinType.getFeePerKb().add(Coin.SATOSHI);
                }
            }

            // Now add unsigned inputs for the selected coins.
            for (TransactionOutput output : selection.gathered) {
                TransactionInput input = req.tx.addInput(output);
                // If the scriptBytes don't default to none, our size calculations will be thrown off.
                checkState(input.getScriptBytes().length == 0);
            }

            // Estimate transaction size and loop again if we need more fee per kb. The serialized tx doesn't
            // include things we haven't added yet like input signatures/scripts or the change output.
            size += req.tx.bitcoinSerialize().length;
            size += estimateBytesForSigning(selection);
            if (size/1000 > lastCalculatedSize/1000 && req.feePerKb.signum() > 0) {
                lastCalculatedSize = size;
                // We need more fees anyway, just try again with the same additional value
                additionalValueForNextCategory = additionalValueSelected;
                continue;
            }

            if (isCategory3) {
                if (selection3 == null)
                    selection3 = selection;
            } else if (eitherCategory2Or3) {
                // If we are in selection2, we will require at least CENT additional. If we do that, there is no way
                // we can end up back here because CENT additional will always get us to 1
                checkState(selection2 == null);
                checkState(additionalValueForNextCategory.equals(Coin.CENT));
                selection2 = selection;
                selection2Change = checkNotNull(changeOutput); // If we get no change in category 2, we are actually in category 3
            } else {
                // Once we get a category 1 (change kept), we should break out of the loop because we can't do better
                checkState(selection1 == null);
                checkState(additionalValueForNextCategory == null);
                selection1 = selection;
                selection1Change = changeOutput;
            }

            if (additionalValueForNextCategory != null) {
                if (additionalValueSelected != null)
                    checkState(additionalValueForNextCategory.compareTo(additionalValueSelected) > 0);
                continue;
            }
            break;
        }

        resetTxInputs(req, originalInputs);

        if (selection3 == null && selection2 == null && selection1 == null) {
            checkNotNull(valueMissing);
            log.warn("Insufficient value in wallet for send: needed {} more", valueMissing.toFriendlyString());
            throw new InsufficientMoneyException(valueMissing);
        }

        Coin lowestFee = null;
        result.bestCoinSelection = null;
        result.bestChangeOutput = null;
        if (selection1 != null) {
            if (selection1Change != null)
                lowestFee = selection1.valueGathered.subtract(selection1Change.getValue());
            else
                lowestFee = selection1.valueGathered;
            result.bestCoinSelection = selection1;
            result.bestChangeOutput = selection1Change;
        }

        if (selection2 != null) {
            Coin fee = selection2.valueGathered.subtract(checkNotNull(selection2Change).getValue());
            if (lowestFee == null || fee.compareTo(lowestFee) < 0) {
                lowestFee = fee;
                result.bestCoinSelection = selection2;
                result.bestChangeOutput = selection2Change;
            }
        }

        if (selection3 != null) {
            if (lowestFee == null || selection3.valueGathered.compareTo(lowestFee) < 0) {
                result.bestCoinSelection = selection3;
                result.bestChangeOutput = null;
            }
        }
        return result;
    }

    /** Reduce the value of the first output of a transaction to pay the given feePerKb as appropriate for its size. */
    private boolean adjustOutputDownwardsForFee(Transaction tx, CoinSelection coinSelection, Coin baseFee, Coin feePerKb) {
        TransactionOutput output = tx.getOutput(0);
        // Check if we need additional fee due to the transaction's size
        int size = tx.bitcoinSerialize().length;
        size += estimateBytesForSigning(coinSelection);
        Coin fee = baseFee.add(feePerKb.multiply((size / 1000) + 1));
        output.setValue(output.getValue().subtract(fee));
        // Check if we need additional fee due to the output's value
        if (output.getValue().compareTo(Coin.CENT) < 0 && fee.compareTo(coinType.getFeePerKb()) < 0)
            output.setValue(output.getValue().subtract(coinType.getFeePerKb().subtract(fee)));
        return output.getMinNonDustValue(coinType.getFeePerKb().multiply(3)).compareTo(output.getValue()) <= 0;
    }

    private int estimateBytesForSigning(CoinSelection selection) {
        int size = 0;
        for (TransactionOutput output : selection.gathered) {
            try {
                Script script = output.getScriptPubKey();
                ECKey key = null;
                Script redeemScript = null;
                if (script.isSentToAddress()) {
                    key = findKeyFromPubHash(script.getPubKeyHash());
                    if (key == null) {
                        log.error("output.getIndex {}", output.getIndex());
                        log.error("output.getAddressFromP2SH {}", output.getAddressFromP2SH(coinType));
                        log.error("output.getAddressFromP2PKHScript {}", output.getAddressFromP2PKHScript(coinType));
                        log.error("output.getParentTransaction().getHash() {}", output.getParentTransaction().getHash());
                    }
                    checkNotNull(key, "Coin selection includes unspendable outputs");
                } else if (script.isPayToScriptHash()) {
                    throw new ScriptException("Wallet does not currently support PayToScriptHash");
//                    redeemScript = keychain.findRedeemScriptFromPubHash(script.getPubKeyHash());
//                    checkNotNull(redeemScript, "Coin selection includes unspendable outputs");
                }
                size += script.getNumberOfBytesRequiredToSpend(key, redeemScript);
            } catch (ScriptException e) {
                // If this happens it means an output script in a wallet tx could not be understood. That should never
                // happen, if it does it means the wallet has got into an inconsistent state.
                throw new IllegalStateException(e);
            }
        }
        return size;
    }

    /**
     * Locates a keypair from the basicKeyChain given the hash of the public key. This is needed
     * when finding out which key we need to use to redeem a transaction output.
     *
     * @return ECKey object or null if no such key was found.
     */
    @Nullable
    public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
        lock.lock();
        try {
            return keys.findKeyFromPubHash(pubkeyHash);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Locates a keypair from the basicKeyChain given the raw public key bytes.
     * @return ECKey or null if no such key was found.
     */
    @Nullable
    public ECKey findKeyFromPubKey(byte[] pubkey) {
        lock.lock();
        try {
            return keys.findKeyFromPubKey(pubkey);
        } finally {
            lock.unlock();
        }
    }

    private void resetTxInputs(SendRequest req, List<TransactionInput> originalInputs) {
        req.tx.clearInputs();
        for (TransactionInput input : originalInputs)
            req.tx.addInput(input);
    }

    /** Returns the address used for change outputs. Note: this will probably go away in future. */
    Address getChangeAddress() {
        return currentAddress(SimpleHDKeyChain.KeyPurpose.CHANGE);
    }

    public Address getReceiveAddress() {
        return currentAddress(SimpleHDKeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    @VisibleForTesting Address currentAddress(SimpleHDKeyChain.KeyPurpose purpose) {
        lock.lock();
        try {
            DeterministicKey key = keys.getCurrentUnusedKey(purpose);
//            DeterministicKey key = keys.getKey(purpose);
            log.info("Current address, get key n. {} {}", key.getChildNumber().num(),
                    key.toAddress(coinType));
            return key.toAddress(coinType);
        } finally {
            lock.unlock();

            subscribeIfNeeded();
        }
    }

    /**
     * Used to force keys creation, could take long time to complete so use it in a background
     * thread.
     */
    @VisibleForTesting void maybeInitializeAllKeys() {
        lock.lock();
        try {
            keys.maybeLookAhead();
        } finally {
            lock.unlock();
        }
    }
}
