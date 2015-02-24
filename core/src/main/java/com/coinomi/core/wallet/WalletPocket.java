/**
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2014 John L. Jegutanis
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
import com.coinomi.core.network.BlockHeader;
import com.coinomi.core.network.ServerClient.HistoryTx;
import com.coinomi.core.network.ServerClient.UnspentTx;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.wallet.exceptions.Bip44KeyLookAheadExceededException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBag;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionInput.ConnectMode;
import org.bitcoinj.core.TransactionInput.ConnectionResult;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VarInt;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.signers.LocalTransactionSigner;
import org.bitcoinj.signers.MissingSigResolutionSigner;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.RedeemData;
import org.bitcoinj.wallet.WalletTransaction;
import org.bitcoinj.wallet.WalletTransaction.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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

import static org.bitcoinj.wallet.KeyChain.KeyPurpose.RECEIVE_FUNDS;
import static org.bitcoinj.wallet.KeyChain.KeyPurpose.CHANGE;

import static com.coinomi.core.Preconditions.checkArgument;
import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 *
 *
 */
public class WalletPocket implements TransactionBag, KeyBag, TransactionEventListener, ConnectionEventListener, Serializable {
    private static final Logger log = LoggerFactory.getLogger(WalletPocket.class);

    final ReentrantLock lock = Threading.lock("WalletPocket");

    private final static int TX_DEPTH_SAVE_THRESHOLD = 4;

    final CoinType coinType;
    private final TransactionCreator transactionCreator;

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

    private final transient CopyOnWriteArrayList<ListenerRegistration<WalletPocketEventListener>> listeners;
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
        transactionCreator = new TransactionCreator(this);
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

    /**
     * Just adds the transaction to a pool without doing anything else
     * @param pool
     * @param tx
     */
    private void simpleAddTransaction(Pool pool, Transaction tx) {
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
                try {
                    if (keys.findKeyFromPubHash(txo.getScriptPubKey().getPubKeyHash()) == null) {
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
    private void addWalletTransaction(Pool pool, Transaction tx, boolean save) {
        lock.lock();
        try {
            if (log.isInfoEnabled()) {
                log.info("Adding {} tx {} to {}",
                        tx.isEveryOwnedOutputSpent(this) ? Pool.SPENT : Pool.UNSPENT, tx.getHash(), pool);
                if (!tx.isEveryOwnedOutputSpent(this)) {
                    for (TransactionOutput transactionOutput : tx.getOutputs()) {
                        log.info("|- {} txo index {}",
                                transactionOutput.isAvailableForSpending() ? Pool.UNSPENT : Pool.SPENT,
                                transactionOutput.getIndex());
                    }
                }
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


    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public Wallet getWallet() {
        return wallet;
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
            // If nothing pending, we are just CONNECTED
            if (addressesPendingSubscription.isEmpty() && statusPendingUpdates.isEmpty() && fetchingTransactions.isEmpty()) {
                return WalletPocketConnectivity.CONNECTED;
            } else {
                // TODO support WORKING state, for now is just CONNECTED
                return WalletPocketConnectivity.CONNECTED;
            }
        }
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
        if (confidence.getConfidenceType() != ConfidenceType.BUILDING) return;
        int newDepth = lastBlockSeenHeight - confidence.getAppearedAtChainHeight() + 1;
        if (newDepth > 1) confidence.setDepthInBlocks(newDepth);
    }

    @Override
    public void onAddressStatusUpdate(AddressStatus status) {
        log.info("Got a status {}", status);
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

        markUnspentTXO(status, txs);

        commitAddressStatus(status);
        queueOnNewBalance();
    }

    private void markUnspentTXO(AddressStatus status, Map<Sha256Hash, Transaction> txs) {
        checkState(lock.isHeldByCurrentThread());
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


                    boolean belongsToAddress = false;
                    try {
                        belongsToAddress = Arrays.equals(txo.getScriptPubKey().getPubKeyHash(),
                                status.getAddress().getHash160());
                    } catch (ScriptException ignore) {
                        // If we cannot understand this output, it doesn't belong to the address
                    }


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
        int txiIndex = 0;
        for (TransactionInput txi : tx.getInputs()) {
            if (txi.getConnectedOutput() != null) {
                log.info("skipping an already connected txi {}", txi);
                txiIndex++;
                continue; // skip connected inputs
            }
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
            log.info("Going to fetch transaction with hash {}", txHash);
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

    private void queueOnNewBlock() {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<WalletPocketEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onNewBlock(WalletPocket.this);
                    registration.listener.onPocketChanged(WalletPocket.this);
                }
            });
        }
    }

    private void queueOnConnectivity() {
        final WalletPocketConnectivity connectivity = getConnectivityStatus();
        for (final ListenerRegistration<WalletPocketEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onConnectivityStatus(connectivity);
                }
            });
        }
    }

    private void queueOnTransactionBroadcastSuccess(final Transaction tx) {
        for (final ListenerRegistration<WalletPocketEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onTransactionBroadcastSuccess(WalletPocket.this, tx);
                }
            });
        }
    }

    private void queueOnTransactionBroadcastFailure(final Transaction tx) {
        for (final ListenerRegistration<WalletPocketEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onTransactionBroadcastFailure(WalletPocket.this, tx);
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
        // Save in another thread to avoid cyclic locking of Wallet and WalletPocket
        Threading.USER_THREAD.execute(saveLaterRunnable);
    }

    private void walletSaveNow() {
        // Save in another thread to avoid cyclic locking of Wallet and WalletPocket
        Threading.USER_THREAD.execute(saveNowRunnable);
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
     * {@link org.bitcoinj.crypto.KeyCrypterScrypt}.
     *
     * @throws org.bitcoinj.crypto.KeyCrypterException Thrown if the wallet encryption fails for some reason,
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
     * KeyCrypter to use is {@link org.bitcoinj.crypto.KeyCrypterScrypt}.
     *
     * @throws org.bitcoinj.crypto.KeyCrypterException Thrown if the wallet decryption fails for some reason, leaving the group unchanged.
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
            transactionCreator.completeTx(req);
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>Given a send request containing transaction, attempts to sign it's inputs. This method expects transaction
     * to have all necessary inputs connected or they will be ignored.</p>
     * <p>Actual signing is done by pluggable {@link org.bitcoinj.signers.LocalTransactionSigner}
     * and it's not guaranteed that transaction will be complete in the end.</p>
     */
    public void signTransaction(SendRequest req) {
        lock.lock();
        try {
            transactionCreator.signTransaction(req);
        } finally {
            lock.unlock();
        }
    }


    /**
     * Locates a keypair from the basicKeyChain given the hash of the public key. This is needed
     * when finding out which key we need to use to redeem a transaction output.
     *
     * @return ECKey object or null if no such key was found.
     */
    @Nullable
    @Override
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
    @Override
    public ECKey findKeyFromPubKey(byte[] pubkey) {
        lock.lock();
        try {
            return keys.findKeyFromPubKey(pubkey);
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] bytes) {
        return null;
    }

    /** Returns the address used for change outputs. Note: this will probably go away in future. */
    public Address getChangeAddress() {
        return currentAddress(CHANGE);
    }

    /**
     * Get current receive address, does not mark it as used
     */
    public Address getReceiveAddress() {
        return currentAddress(RECEIVE_FUNDS);
    }

    /**
     * Get the last used receiving address
     */
    @Nullable
    public Address getLastUsedReceiveAddress() {
        lock.lock();
        try {
            DeterministicKey lastUsedKey = keys.getLastIssuedKey(RECEIVE_FUNDS);
            if (lastUsedKey != null) {
                return lastUsedKey.toAddress(coinType);
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns true is it is possible to create new fresh receive addresses, false otherwise
     */
    public boolean canCreateFreshReceiveAddress() {
        lock.lock();
        try {
            DeterministicKey currentUnusedKey = keys.getCurrentUnusedKey(RECEIVE_FUNDS);
            int maximumKeyIndex = SimpleHDKeyChain.LOOKAHEAD - 1;

            // If there are used keys
            if (!addressesStatus.isEmpty()) {
                int lastUsedKeyIndex = 0;
                // Find the last used key index
                for (Map.Entry<Address, String> entry : addressesStatus.entrySet()) {
                    if (entry.getValue() == null) continue;
                    DeterministicKey usedKey = keys.findKeyFromPubHash(entry.getKey().getHash160());
                    if (usedKey != null && keys.isExternal(usedKey) && usedKey.getChildNumber().num() > lastUsedKeyIndex) {
                        lastUsedKeyIndex = usedKey.getChildNumber().num();
                    }
                }
                maximumKeyIndex = lastUsedKeyIndex + SimpleHDKeyChain.LOOKAHEAD;
            }

            log.info("Maximum key index for new key is {}", maximumKeyIndex);

            // If we exceeded the BIP44 look ahead threshold
            return currentUnusedKey.getChildNumber().num() < maximumKeyIndex;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get a fresh address by marking the current receive address as used. It will throw
     * {@link Bip44KeyLookAheadExceededException} if we requested too many addresses that
     * exceed the BIP44 look ahead threshold.
     */
    public Address getFreshReceiveAddress() throws Bip44KeyLookAheadExceededException {
        lock.lock();
        try {
            if (!canCreateFreshReceiveAddress()) {
                throw new Bip44KeyLookAheadExceededException();
            }
            keys.getKey(RECEIVE_FUNDS);
            return currentAddress(RECEIVE_FUNDS);
        } finally {
            lock.unlock();
            walletSaveNow();
        }
    }

    private static final Comparator<DeterministicKey> HD_KEY_COMPARATOR =
            new Comparator<DeterministicKey>() {
                @Override
                public int compare(final DeterministicKey k1, final DeterministicKey k2) {
                    int key1Num = k1.getChildNumber().num();
                    int key2Num = k2.getChildNumber().num();
                    // In reality Integer.compare(key2Num, key1Num) but is not available on older devices
                    return (key2Num < key1Num) ? -1 : ((key2Num == key1Num) ? 0 : 1);
                }
            };

    /**
     * Returns the number of issued receiving keys
     */
    public int getNumberIssuedReceiveAddresses() {
        lock.lock();
        try {
            return keys.getNumIssuedExternalKeys();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a list of addresses that have been issued.
     * The list is sorted in descending chronological order: older in the end
     */
    public List<Address> getIssuedReceiveAddresses() {
        lock.lock();
        try {
            ArrayList<DeterministicKey> issuedKeys = keys.getIssuedExternalKeys();
            ArrayList<Address> receiveAddresses = new ArrayList<Address>();

            Collections.sort(issuedKeys, HD_KEY_COMPARATOR);

            for (ECKey key : issuedKeys) {
                receiveAddresses.add(key.toAddress(coinType));
            }
            return receiveAddresses;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the currently used receiving and change addresses
     */
    public Set<Address> getUsedAddresses() {
        lock.lock();
        try {
            HashSet<Address> usedAddresses = new HashSet<Address>();

            for (Map.Entry<Address, String> entry : addressesStatus.entrySet()) {
                if (entry.getValue() != null) {
                    usedAddresses.add(entry.getKey());
                }
            }

            return usedAddresses;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the currently latest unused address by purpose.
     */
    @VisibleForTesting Address currentAddress(SimpleHDKeyChain.KeyPurpose purpose) {
        lock.lock();
        try {
            return keys.getCurrentUnusedKey(purpose).toAddress(coinType);
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
