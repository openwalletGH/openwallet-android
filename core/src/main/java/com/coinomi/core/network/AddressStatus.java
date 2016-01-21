package com.coinomi.core.network;

import com.coinomi.core.network.ServerClient.HistoryTx;
import com.coinomi.core.network.ServerClient.UnspentTx;
import com.coinomi.core.wallet.AbstractAddress;
import com.google.common.collect.Sets;

import org.bitcoinj.core.Sha256Hash;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
final public class AddressStatus {
    final AbstractAddress address;
    @Nullable final String status;

    HashSet<HistoryTx> historyTransactions;
    HashSet<UnspentTx> unspentTransactions;
    HashSet<Sha256Hash> historyTxHashes = new HashSet<>();
    HashSet<Sha256Hash> unspentTxHashes = new HashSet<>();

    boolean stateMustBeApplied;
    boolean historyTxStateApplied;
    boolean unspentTxStateApplied;

    public AddressStatus(AbstractAddress address, @Nullable String status) {
        this.address = address;
        this.status = status;
    }

    public AbstractAddress getAddress() {
        return address;
    }

    @Nullable public String getStatus() {
        return status;
    }

    /**
     * Queue transactions that are going to be fetched
     */
    public void queueHistoryTransactions(List<HistoryTx> txs) {
        if (historyTransactions == null) {
            historyTransactions = Sets.newHashSet(txs);
            historyTxHashes = fillTransactions(txs);
            stateMustBeApplied = true;
        }
    }

    /**
     * Queue transactions that are going to be fetched
     */
    public void queueUnspentTransactions(List<UnspentTx> txs) {
        if (unspentTransactions == null) {
            unspentTransactions = Sets.newHashSet(txs);
            unspentTxHashes = fillTransactions(txs);
            stateMustBeApplied = true;
        }
    }

    private HashSet<Sha256Hash> fillTransactions(Iterable<? extends HistoryTx> txs) {
        HashSet<Sha256Hash> transactionHashes = new HashSet<>();
        for (HistoryTx tx : txs) {
            transactionHashes.add(tx.getTxHash());
        }
        return transactionHashes;
    }

    /**
     * Return true if history transactions are queued
     */
    public boolean isHistoryTxQueued() {
        return historyTransactions != null;
    }

    /**
     * Return true if unspent transactions are queued
     */
    public boolean isUnspentTxQueued() {
        return unspentTransactions != null;
    }

    /**
     * Get queued history transactions
     */
    public Set<Sha256Hash> getHistoryTxHashes() {
        return historyTxHashes;
    }

    /**
     * Get queued unspent transactions
     */
    public Set<Sha256Hash> getUnspentTxHashes() {
        return unspentTxHashes;
    }

    /**
     * Get history transactions info
     */
    public HashSet<HistoryTx> getHistoryTxs() {
        return historyTransactions;
    }

    /**
     * Get unspent transactions info
     */
    public HashSet<UnspentTx> getUnspentTxs() {
        return unspentTransactions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AddressStatus status1 = (AddressStatus) o;

        if (!address.equals(status1.address)) return false;
        if (status != null ? !status.equals(status1.status) : status1.status != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + (status != null ? status.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "{" +
                "address=" + address +
                ", status='" + status + '\'' +
                '}';
    }

    public boolean isHistoryTxStateApplied() {
        return historyTxStateApplied;
    }

    public void setHistoryTxStateApplied(boolean historyTxStateApplied) {
        this.historyTxStateApplied = historyTxStateApplied;
    }

    public boolean isUnspentTxStateApplied() {
        return unspentTxStateApplied;
    }

    public void setUnspentTxStateApplied(boolean unspentTxStateApplied) {
        this.unspentTxStateApplied = unspentTxStateApplied;
    }

    public boolean canCommitStatus() {
        return !stateMustBeApplied || historyTxStateApplied && unspentTxStateApplied;
    }
}
