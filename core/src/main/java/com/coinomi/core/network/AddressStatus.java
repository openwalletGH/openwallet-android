package com.coinomi.core.network;

import org.bitcoinj.core.Sha256Hash;

import com.coinomi.core.wallet.AbstractAddress;
import com.google.common.collect.Sets;

import com.coinomi.core.network.ServerClient.HistoryTx;

import java.util.HashSet;
import java.util.List;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
final public class AddressStatus {
    final AbstractAddress address;
    @Nullable final String status;

    HashSet<HistoryTx> historyTransactions;
    HashSet<Sha256Hash> allTransactions = new HashSet<>();

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
            historyTransactions = fillTransactions(txs);
        }
    }

    private HashSet<HistoryTx> fillTransactions(List<HistoryTx> txs) {
        HashSet<HistoryTx> txSet = Sets.newHashSet(txs);
        for (HistoryTx tx : txs) {
            allTransactions.add(tx.getTxHash());
        }
        return txSet;
    }

    /**
     * Return true if history and unspent transactions are queued
     */
    public boolean isReady() {
        return historyTransactions != null;
    }

    /**
     * Get all queued transactions
     */
    public HashSet<Sha256Hash> getAllTransactionHashes() {
        return allTransactions;
    }

    /**
     * Get history transactions info
     */
    public HashSet<HistoryTx> getHistoryTxs() {
        return historyTransactions;
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
}
