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
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.network.ServerClient.UnspentTx;
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
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VarInt;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.utils.ListenerRegistration;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.wallet.CoinSelection;
import com.google.bitcoin.wallet.CoinSelector;
import com.google.bitcoin.wallet.DefaultCoinSelector;
import com.google.bitcoin.wallet.WalletTransaction;
import com.google.bitcoin.wallet.WalletTransaction.Pool;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Giannis Dzegoutanis
 *
 *
 */
public class WalletPocket implements TransactionEventListener, ConnectionEventListener, Serializable {
    private static final Logger log = LoggerFactory.getLogger(Wallet.class);
    private final ReentrantLock lock = Threading.lock("WalletPocket");

    private final CoinType coinType;

    private String description;

    @Nullable private Sha256Hash lastBlockSeenHash;
    private int lastBlockSeenHeight;
    private long lastBlockSeenTimeSecs;

    // Holds the status of every address we are watching. When connecting to the server, if we get a
    // different status for a particular address this means that there are new transactions for that
    // address and we have to fetch them.
    @VisibleForTesting final HashMap<Address, String> addressesStatus;

    @VisibleForTesting final transient ArrayList<Address> addressesSubscribed;
    @VisibleForTesting final transient ArrayList<Address> addressesPendingSubscription;
    @VisibleForTesting final transient HashMap<String, List<UnspentTx>> statusPendingUpdate;

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

    @VisibleForTesting final SimpleHDKeyChain keys;

    @Nullable private transient BlockchainConnection blockchainConnection;

    protected transient CoinSelector coinSelector = new DefaultCoinSelector();

    private final transient CopyOnWriteArrayList<ListenerRegistration<WalletPocketEventListener>> listeners;

    public WalletPocket(DeterministicKey rootKey, CoinType coinType) {
        this(new SimpleHDKeyChain(rootKey), coinType);
    }

    WalletPocket(SimpleHDKeyChain keys, CoinType coinType) {
        this.keys = checkNotNull(keys);
        this.coinType = checkNotNull(coinType);
        addressesStatus = new HashMap<Address, String>(2 * SimpleHDKeyChain.LOOKAHEAD);
        addressesSubscribed = new ArrayList(2 * SimpleHDKeyChain.LOOKAHEAD);
        addressesPendingSubscription = new ArrayList(2 * SimpleHDKeyChain.LOOKAHEAD);
//        statusPendingUpdate = new ArrayList(2 * SimpleHDKeyChain.LOOKAHEAD);
        statusPendingUpdate = new HashMap<String, List<UnspentTx>>(2 * SimpleHDKeyChain.LOOKAHEAD);
        unspent = new HashMap<Sha256Hash, Transaction>();
        spent = new HashMap<Sha256Hash, Transaction>();
        pending = new HashMap<Sha256Hash, Transaction>();
        dead = new HashMap<Sha256Hash, Transaction>();
        transactions = new HashMap<Sha256Hash, Transaction>();
        listeners = new CopyOnWriteArrayList<ListenerRegistration<WalletPocketEventListener>>();
    }


    /******************************************************************************************************************/

    //region Vending transactions and other internal state

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

    public CoinType getCoinType() {
        return coinType;
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
    }

    public void setLastBlockSeenHeight(int lastBlockSeenHeight) {
        lock.lock();
        try {
            this.lastBlockSeenHeight = lastBlockSeenHeight;
        } finally {
            lock.unlock();
        }
    }

    public void setLastBlockSeenTimeSecs(long timeSecs) {
        lock.lock();
        try {
            lastBlockSeenTimeSecs = timeSecs;
        } finally {
            lock.unlock();
        }
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
        this.description = description;
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
        if (includeUnconfirmed) {
            return getTxBalance(Iterables.concat(unspent.values(), pending.values()));
        }
        return getTxBalance(unspent.values());
    }

    Coin getTxBalance(Iterable<Transaction> txs) {
        lock.lock();
        try {
            Coin value = Coin.ZERO;
            for (Transaction tx : txs) {
                for (TransactionOutput txo : tx.getOutputs()) {
                    if (txo.isAvailableForSpending()) {
                        value = value.add(txo.getValue());
                    }
                }
            }
            return value;
        }
        finally {
            lock.unlock();
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
    @VisibleForTesting boolean registerStatusForUpdate(String status) {
        checkNotNull(status);

        lock.lock();
        try {
            if (statusPendingUpdate.containsKey(status)) {
                return false;
            }
            else {
                statusPendingUpdate.put(status, new ArrayList<UnspentTx>());
                return true;
            }
        }
        finally {
            lock.unlock();
        }
    }

    @VisibleForTesting boolean registerTransactionsForUpdate(String status, Collection<UnspentTx> txHashes) {
        checkNotNull(status);

        lock.lock();
        try {
            if (statusPendingUpdate.containsKey(status)) {
                checkState(statusPendingUpdate.get(status).size() == 0, "Transactions already registered");
                statusPendingUpdate.get(status).addAll(txHashes);
                return true;
            }
            else {
                return false;
            }
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Set that the status updated successfully
     *
     * Returns true if this status was pending update
     */
    @VisibleForTesting void clearPendingStatusUpdate(AddressStatus newStatus, UnspentTx txHash) {
        lock.lock();
        try {
            String status = newStatus.getStatus();

            if (statusPendingUpdate.containsKey(status)) {
                List<UnspentTx> txHashes = statusPendingUpdate.get(status);
                txHashes.remove(txHash);
                if (txHashes.size() == 0) {
                    statusPendingUpdate.remove(status);
                    updateAddressStatus(newStatus);
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    private void updateAddressStatus(AddressStatus newStatus) {
        lock.lock();
        try {
            addressesStatus.put(newStatus.getAddress(), newStatus.getStatus());
        }
        finally {
            lock.unlock();
        }
    }
    private boolean isAddressStatusChanged(AddressStatus status) {
        lock.lock();
        try {
            if (addressesStatus.containsKey(status.getAddress())) {
                return !addressesStatus.get(status.getAddress()).equals(status.getStatus());
            }
            else {
                // Unused address, just mark it that we watch it
                if (status.getStatus() == null) {
                    updateAddressStatus(status);
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

    public List<AddressStatus> getAddressesStatus() {
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
        lock.lock();
        try {
            confirmAddressSubscription(status.getAddress());
            if (status.getStatus() != null) {
                keys.markPubHashAsUsed(status.getAddress().getHash160());
                subscribeIfNeeded();

                if (isAddressStatusChanged(status)) {
                    // Status changed, time to update
                    if (registerStatusForUpdate(status.getStatus())) {
                        log.info("Must get UTXOs for address {}, status changes {}", status.getAddress(),
                                status.getStatus());

                        if (blockchainConnection != null) {
                            blockchainConnection.getUnspentTx(coinType, status, this);
                        }
                    }
                    else {
                        log.info("Status {} already updating", status.getStatus());
                    }
                }
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
            if (unspentTxes.size() > 0) {
                log.info("Got {} unspent transactions for address {}", unspentTxes.size(),
                        status.getAddress());
                List<UnspentTx> txToGet = new ArrayList<UnspentTx>();
                for (UnspentTx tx : unspentTxes) {
                    log.info("- utxo {} worth {}", tx.getTxHash(), tx.getValue());
                    if (!unspent.containsKey(tx.getTxHash())) {
                        txToGet.add(tx);
                    } else {
                        markAsUnspent(tx);
                    }
                }
                if (txToGet.size() > 0) {
                    registerTransactionsForUpdate(status.getStatus(), txToGet);
                    for (UnspentTx tx : txToGet) {
                        log.info("Must get raw transaction {}", tx.getTxHash());
                        if (blockchainConnection != null) {
                            blockchainConnection.getTx(coinType, status, tx, this);
                        }
                    }
                }
                else { // If not fetching any transaction, update status now
                    updateAddressStatus(status);
                }
            }
            else {
                log.info("No unspent transactions for address {}", status.getAddress());
                updateAddressStatus(status);
            }

        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void onTransactionUpdate(AddressStatus status, UnspentTx utx, byte[] rawTx) {
        lock.lock();
        try {
            if (!unspent.containsKey(utx.getTxHash())) {
                Transaction newTx = new Transaction(coinType, rawTx);
                newTx.getConfidence().setAppearedAtChainHeight(utx.getHeight());
                for (TransactionOutput txo : newTx.getOutputs()) {
                    txo.markAsSpent(null);
                }
                unspent.put(utx.getTxHash(), newTx);
            }
            markAsUnspent(utx);
            queueOnNewBalance();
            clearPendingStatusUpdate(status, utx);
        }
        finally {
            lock.unlock();
        }
    }

    private void markAsUnspent(UnspentTx utx) {
        lock.lock();
        try {
            unspent.get(utx.getTxHash()).getOutput(utx.getTxPos()).markAsUnspent();
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void onConnection(BlockchainConnection blockchainConnection) {
        this.blockchainConnection = blockchainConnection;
        this.addressesSubscribed.clear();
        subscribeIfNeeded();
    }

    private void subscribeIfNeeded() {
        lock.lock();
        try {
            List<Address> addressesToWatch = getAddressesToWatch();
            if (addressesToWatch.size() > 0) {
                addressesPendingSubscription.addAll(addressesToWatch);
                blockchainConnection.subscribeToAddresses(coinType, addressesToWatch, this);
            }
        } catch (Exception e) {
            log.error("Error subscribing to addresses", e);
            return;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void onDisconnect() {
        this.blockchainConnection = null;
        this.addressesSubscribed.clear();
    }

    public void sendCoins(Address address, Coin amount) throws InsufficientMoneyException {
        SendRequest request = SendRequest.to(address, amount);
        request.feePerKb = coinType.getFeePerKb();

        Transaction tx = sendCoinsOffline(request);


        log.info("Created tx {}", Utils.HEX.encode(tx.bitcoinSerialize()));
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
        final Coin newBalance = getBalance();
        for (final ListenerRegistration<WalletPocketEventListener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onNewBalance(newBalance);
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
            return WalletPocketSerializer.toProtobuf(this);
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
    public Transaction sendCoinsOffline(SendRequest request) throws InsufficientMoneyException {
        lock.lock();
        try {
            completeTx(request);
//            commitTx(request.tx);
            return request.tx;
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
                        if (output.getValue().compareTo(output.getMinNonDustValue()) < 0)
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
//                req.tx.signInputs(Transaction.SigHash.ALL, this, req.aesKey);
                req.tx.signInputs(Transaction.SigHash.ALL, true, keys);
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
            if (needAtLeastReferenceFee && fees.compareTo(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE) < 0)
                fees = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;

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
                    change.compareTo(Coin.CENT) < 0 && fees.compareTo(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE) < 0) {
                // This solution may fit into category 2, but it may also be category 3, we'll check that later
                eitherCategory2Or3 = true;
                additionalValueForNextCategory = Coin.CENT;
                // If the change is smaller than the fee we want to add, this will be negative
                change = change.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.subtract(fees));
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
                if (req.ensureMinRequiredFee && Transaction.MIN_NONDUST_OUTPUT.compareTo(change) >= 0) {
                    // This solution definitely fits in category 3
                    isCategory3 = true;
                    additionalValueForNextCategory = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.add(
                            Transaction.MIN_NONDUST_OUTPUT.add(Coin.SATOSHI));
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
                    additionalValueForNextCategory = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.add(Coin.SATOSHI);
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
        if (output.getValue().compareTo(Coin.CENT) < 0 && fee.compareTo(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE) < 0)
            output.setValue(output.getValue().subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.subtract(fee)));
        return output.getMinNonDustValue().compareTo(output.getValue()) <= 0;
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

    public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
        lock.lock();
        try {
            return keys.findKeyFromPubHash(pubkeyHash);
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

    Address currentAddress(SimpleHDKeyChain.KeyPurpose purpose) {
        lock.lock();
        try {
            // TODO make return an unused address
            return keys.getKey(purpose).toAddress(coinType);
        } finally {
            lock.unlock();

            subscribeIfNeeded();
        }
    }
}
