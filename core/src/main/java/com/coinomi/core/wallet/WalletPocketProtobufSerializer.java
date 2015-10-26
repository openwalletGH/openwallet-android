/**
 * Copyright 2012 Google Inc.
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

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.protos.Protos;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.params.Networks.Family.CLAMS;
import static org.bitcoinj.params.Networks.Family.NUBITS;
import static org.bitcoinj.params.Networks.Family.PEERCOIN;
import static org.bitcoinj.params.Networks.Family.REDDCOIN;
import static org.bitcoinj.params.Networks.Family.VPNCOIN;
import static org.bitcoinj.params.Networks.isFamily;

/**
 * @author John L. Jegutanis
 */
public class WalletPocketProtobufSerializer {
    private static final Logger log = LoggerFactory.getLogger(WalletPocketProtobufSerializer.class);

    // Used for de-serialization
    protected Map<ByteString, Transaction> txMap = new HashMap<ByteString, Transaction>();

    public static Protos.WalletPocket toProtobuf(WalletPocketHD pocket) {

        Protos.WalletPocket.Builder walletBuilder = Protos.WalletPocket.newBuilder();
        walletBuilder.setNetworkIdentifier(pocket.getCoinType().getId());
        if (pocket.getDescription() != null) {
            walletBuilder.setDescription(pocket.getDescription());
        }
        if (pocket.getId() != null) {
            walletBuilder.setId(pocket.getId());
        }

        for (AddressStatus status : pocket.getAllAddressStatus()) {
            Protos.AddressStatus.Builder addressStatus = Protos.AddressStatus.newBuilder();
            if (status.getStatus() == null) {
                continue; // Don't serialize null statuses
            }
            addressStatus.setAddress(status.getAddress().toString());
            addressStatus.setStatus(status.getStatus()); // TODO check null values

            walletBuilder.addAddressStatus(addressStatus.build());
        }

        for (WalletTransaction wtx : pocket.getWalletTransactions()) {
            Protos.Transaction txProto = makeTxProto(wtx);
            walletBuilder.addTransaction(txProto);
        }

        walletBuilder.addAllKey(pocket.serializeKeychainToProtobuf());

        // Populate the lastSeenBlockHash field.
        if (pocket.getLastBlockSeenHash() != null) {
            walletBuilder.setLastSeenBlockHash(hashToByteString(pocket.getLastBlockSeenHash()));
            walletBuilder.setLastSeenBlockHeight(pocket.getLastBlockSeenHeight());
        }
        if (pocket.getLastBlockSeenTimeSecs() > 0) {
            walletBuilder.setLastSeenBlockTimeSecs(pocket.getLastBlockSeenTimeSecs());
        }

        return walletBuilder.build();
    }

    private static Protos.Transaction makeTxProto(WalletTransaction wtx) {
        Transaction tx = wtx.getTransaction();
        Protos.Transaction.Builder txBuilder = Protos.Transaction.newBuilder();


        txBuilder.setPool(getProtoPool(wtx))
                .setHash(hashToByteString(tx.getHash()))
                .setVersion((int) tx.getVersion());

        if (isFamily(tx.getParams(), PEERCOIN, NUBITS, REDDCOIN, VPNCOIN, CLAMS)) {
            txBuilder.setTime((int) tx.getTime());
        }

        if (isFamily(tx.getParams(), NUBITS)) {
            txBuilder.setTokenId(tx.getTokenId());
        }

        if (tx.getExtraBytes() != null && (isFamily(tx.getParams(), VPNCOIN) || (isFamily(tx.getParams(), CLAMS) && tx.getVersion() > 1))) {
            txBuilder.setExtraBytes(ByteString.copyFrom(tx.getExtraBytes()));
        }

        if (tx.getUpdateTime() != null) {
            txBuilder.setUpdatedAt(tx.getUpdateTime().getTime());
        }

        if (tx.getLockTime() > 0) {
            txBuilder.setLockTime((int)tx.getLockTime());
        }

        // Handle inputs.
        for (TransactionInput input : tx.getInputs()) {
            Protos.TransactionInput.Builder inputBuilder = Protos.TransactionInput.newBuilder()
                    .setScriptBytes(ByteString.copyFrom(input.getScriptBytes()))
                    .setTransactionOutPointHash(hashToByteString(input.getOutpoint().getHash()))
                    .setTransactionOutPointIndex((int) input.getOutpoint().getIndex());
            if (input.hasSequence())
                inputBuilder.setSequence((int) input.getSequenceNumber());
            if (input.getValue() != null)
                inputBuilder.setValue(input.getValue().value);
            txBuilder.addTransactionInput(inputBuilder);
        }

        // Handle outputs.
        for (TransactionOutput output : tx.getOutputs()) {
            Protos.TransactionOutput.Builder outputBuilder = Protos.TransactionOutput.newBuilder()
                    .setScriptBytes(ByteString.copyFrom(output.getScriptBytes()))
                    .setValue(output.getValue().value);
            final TransactionInput spentBy = output.getSpentBy();
            if (spentBy != null) {
                Sha256Hash spendingHash = spentBy.getParentTransaction().getHash();
                int spentByTransactionIndex = spentBy.getParentTransaction().getInputs().indexOf(spentBy);
                outputBuilder.setSpentByTransactionHash(hashToByteString(spendingHash))
                        .setSpentByTransactionIndex(spentByTransactionIndex);
            }
            txBuilder.addTransactionOutput(outputBuilder);
        }

        // Handle which blocks tx was seen in.
        final Map<Sha256Hash, Integer> appearsInHashes = tx.getAppearsInHashes();
        if (appearsInHashes != null) {
            for (Map.Entry<Sha256Hash, Integer> entry : appearsInHashes.entrySet()) {
                txBuilder.addBlockHash(hashToByteString(entry.getKey()));
                txBuilder.addBlockRelativityOffsets(entry.getValue());
            }
        }

        if (tx.hasConfidence()) {
            TransactionConfidence confidence = tx.getConfidence();
            Protos.TransactionConfidence.Builder confidenceBuilder = Protos.TransactionConfidence.newBuilder();
            writeConfidence(txBuilder, confidence, confidenceBuilder);
        }

        return txBuilder.build();
    }

    private static Protos.Transaction.Pool getProtoPool(WalletTransaction wtx) {
        switch (wtx.getPool()) {
            case UNSPENT: return Protos.Transaction.Pool.UNSPENT;
            case SPENT: return Protos.Transaction.Pool.SPENT;
            case DEAD: return Protos.Transaction.Pool.DEAD;
            case PENDING: return Protos.Transaction.Pool.PENDING;
            default:
                throw new RuntimeException("Unreachable");
        }
    }

    private static void writeConfidence(Protos.Transaction.Builder txBuilder,
                                        TransactionConfidence confidence,
                                        Protos.TransactionConfidence.Builder confidenceBuilder) {
        synchronized (confidence) {
            confidenceBuilder.setType(Protos.TransactionConfidence.Type.valueOf(confidence.getConfidenceType().getValue()));
            if (confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                confidenceBuilder.setAppearedAtHeight(confidence.getAppearedAtChainHeight());
                confidenceBuilder.setDepth(confidence.getDepthInBlocks());
            }
            if (confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.DEAD) {
                // Copy in the overriding transaction, if available.
                // (A dead coinbase transaction has no overriding transaction).
                if (confidence.getOverridingTransaction() != null) {
                    Sha256Hash overridingHash = confidence.getOverridingTransaction().getHash();
                    confidenceBuilder.setOverridingTransaction(hashToByteString(overridingHash));
                }
            }
            TransactionConfidence.Source source = confidence.getSource();
            switch (source) {
                case SELF: confidenceBuilder.setSource(Protos.TransactionConfidence.Source.SOURCE_SELF); break;
                case NETWORK: confidenceBuilder.setSource(Protos.TransactionConfidence.Source.SOURCE_NETWORK); break;
                case UNKNOWN:
                    // Fall through.
                default:
                    confidenceBuilder.setSource(Protos.TransactionConfidence.Source.SOURCE_UNKNOWN); break;
            }
        }

        for (ListIterator<PeerAddress> it = confidence.getBroadcastBy(); it.hasNext();) {
            PeerAddress address = it.next();
            Protos.PeerAddress proto = Protos.PeerAddress.newBuilder()
                    .setIpAddress(ByteString.copyFrom(address.getAddr().getAddress()))
                    .setPort(address.getPort())
                    .setServices(address.getServices().longValue())
                    .build();
            confidenceBuilder.addBroadcastBy(proto);
        }
        txBuilder.setConfidence(confidenceBuilder);
    }

    public static ByteString hashToByteString(Sha256Hash hash) {
        return ByteString.copyFrom(hash.getBytes());
    }

    public static Sha256Hash byteStringToHash(ByteString bs) {
        return new Sha256Hash(bs.toByteArray());
    }

    /**
     * <p>Loads wallet data from the given protocol buffer and inserts it into the given Wallet object. This is primarily
     * useful when you wish to pre-register extension objects. Note that if loading fails the provided Wallet object
     * may be in an indeterminate state and should be thrown away.</p>
     *
     * <p>A wallet can be unreadable for various reasons, such as inability to open the file, corrupt data, internally
     * inconsistent data, a wallet extension marked as mandatory that cannot be handled and so on. You should always
     * handle {@link UnreadableWalletException} and communicate failure to the user in an appropriate manner.</p>
     *
     * @throws UnreadableWalletException thrown in various error conditions (see description).
     */
    public WalletPocketHD readWallet(Protos.WalletPocket walletProto, @Nullable KeyCrypter keyCrypter) throws UnreadableWalletException {
        CoinType coinType;
        try {
            coinType = CoinID.typeFromId(walletProto.getNetworkIdentifier());
        } catch (IllegalArgumentException e) {
            throw new UnreadableWalletException("Unknown network parameters ID " + walletProto.getNetworkIdentifier());
        }

        // Read the scrypt parameters that specify how encryption and decryption is performed.
        SimpleHDKeyChain chain;
        if (keyCrypter != null) {
            chain = SimpleHDKeyChain.fromProtobuf(walletProto.getKeyList(), keyCrypter);
        } else {
            chain = SimpleHDKeyChain.fromProtobuf(walletProto.getKeyList());
        }

        WalletPocketHD pocket;
        if (walletProto.hasId()) {
            pocket = new WalletPocketHD(walletProto.getId(), chain, coinType);
        } else {
            pocket = new WalletPocketHD(chain, coinType);
        }

        if (walletProto.hasDescription()) {
            pocket.setDescription(walletProto.getDescription());
        }

        try {
            // Read all transactions and insert into the txMap.
            for (Protos.Transaction txProto : walletProto.getTransactionList()) {
                readTransaction(txProto, coinType);
            }

            // Update transaction outputs to point to inputs that spend them
            ArrayList<WalletTransaction> wtxs = new ArrayList<WalletTransaction>(walletProto.getTransactionList().size());
            for (Protos.Transaction txProto : walletProto.getTransactionList()) {
                wtxs.add(connectTransactionOutputs(txProto));
            }

            pocket.restoreWalletTransactions(wtxs);

            // Read all the address statuses
            try {
                for (Protos.AddressStatus sp : walletProto.getAddressStatusList()) {
                    Address addr = new Address(coinType, sp.getAddress());
                    AddressStatus status = new AddressStatus(addr, sp.getStatus());
                    pocket.commitAddressStatus(status);
                }
            } catch (AddressFormatException e) {
                throw new UnreadableWalletException(e.getMessage(), e);
            }

            // Update the lastBlockSeenHash.
            if (!walletProto.hasLastSeenBlockHash()) {
                pocket.setLastBlockSeenHash(null);
            } else {
                pocket.setLastBlockSeenHash(byteStringToHash(walletProto.getLastSeenBlockHash()));
            }
            if (!walletProto.hasLastSeenBlockHeight()) {
                pocket.setLastBlockSeenHeight(-1);
            } else {
                pocket.setLastBlockSeenHeight(walletProto.getLastSeenBlockHeight());
            }
            // Will default to zero if not present.
            pocket.setLastBlockSeenTimeSecs(walletProto.getLastSeenBlockTimeSecs());

        } catch (UnreadableWalletException e) {
            log.warn("Could not restore transactions. Trying refreshing {} pocket.", coinType.getName());
            pocket.refresh();
        } finally {
            // Make sure the object can be re-used to read another wallet without corruption.
            txMap.clear();
        }

        return pocket;
    }

    private void readTransaction(Protos.Transaction txProto, CoinType params) throws UnreadableWalletException {
        Transaction tx = new Transaction(params);

        tx.setVersion(txProto.getVersion());

        if (isFamily(tx.getParams(), PEERCOIN, NUBITS, REDDCOIN, VPNCOIN, CLAMS)) {
            tx.setTime(txProto.getTime());
        }

        if (isFamily(tx.getParams(), NUBITS)) {
            tx.setTokenId((byte) (0xFF & txProto.getTokenId()));
        }

        if (txProto.hasExtraBytes() && (isFamily(tx.getParams(), VPNCOIN) || (isFamily(tx.getParams(), CLAMS) && tx.getVersion() > 1))) {
            tx.setExtraBytes(txProto.getExtraBytes().toByteArray());
        }

        if (txProto.hasUpdatedAt()) {
            tx.setUpdateTime(new Date(txProto.getUpdatedAt()));
        }

        for (Protos.TransactionOutput outputProto : txProto.getTransactionOutputList()) {
            Coin value = Coin.valueOf(outputProto.getValue());
            byte[] scriptBytes = outputProto.getScriptBytes().toByteArray();
            TransactionOutput output = new TransactionOutput(params, tx, value, scriptBytes);
            tx.addOutput(output);
        }

        for (Protos.TransactionInput inputProto : txProto.getTransactionInputList()) {
            byte[] scriptBytes = inputProto.getScriptBytes().toByteArray();
            TransactionOutPoint outpoint = new TransactionOutPoint(params,
                    inputProto.getTransactionOutPointIndex() & 0xFFFFFFFFL,
                    byteStringToHash(inputProto.getTransactionOutPointHash())
            );
            Coin value = inputProto.hasValue() ? Coin.valueOf(inputProto.getValue()) : null;
            TransactionInput input = new TransactionInput(params, tx, scriptBytes, outpoint, value);
            if (inputProto.hasSequence()) {
                input.setSequenceNumber(inputProto.getSequence());
            }
            tx.addInput(input);
        }

        for (int i = 0; i < txProto.getBlockHashCount(); i++) {
            ByteString blockHash = txProto.getBlockHash(i);
            int relativityOffset = 0;
            if (txProto.getBlockRelativityOffsetsCount() > 0)
                relativityOffset = txProto.getBlockRelativityOffsets(i);
            tx.addBlockAppearance(byteStringToHash(blockHash), relativityOffset);
        }

        if (txProto.hasLockTime()) {
            tx.setLockTime(0xffffffffL & txProto.getLockTime());
        }

//        tx.setPurpose(Transaction.Purpose.USER_PAYMENT);

        // Transaction should now be complete.
        Sha256Hash protoHash = byteStringToHash(txProto.getHash());
        if (!tx.getHash().equals(protoHash))
            throw new UnreadableWalletException(String.format("Transaction did not deserialize completely: %s vs %s", tx.getHash(), protoHash));
        if (txMap.containsKey(txProto.getHash()))
            throw new UnreadableWalletException("Wallet contained duplicate transaction " + byteStringToHash(txProto.getHash()));
        txMap.put(txProto.getHash(), tx);
    }

    private WalletTransaction connectTransactionOutputs(Protos.Transaction txProto) throws UnreadableWalletException {
        Transaction tx = txMap.get(txProto.getHash());
        final WalletTransaction.Pool pool;
        switch (txProto.getPool()) {
            case DEAD: pool = WalletTransaction.Pool.DEAD; break;
            case PENDING: pool = WalletTransaction.Pool.PENDING; break;
            case SPENT: pool = WalletTransaction.Pool.SPENT; break;
            case UNSPENT: pool = WalletTransaction.Pool.UNSPENT; break;
            default:
                throw new UnreadableWalletException("Unknown transaction pool: " + txProto.getPool());
        }
        for (int i = 0 ; i < tx.getOutputs().size() ; i++) {
            TransactionOutput output = tx.getOutputs().get(i);
            final Protos.TransactionOutput transactionOutput = txProto.getTransactionOutput(i);
            if (transactionOutput.hasSpentByTransactionHash()) {
                final ByteString spentByTransactionHash = transactionOutput.getSpentByTransactionHash();
                Transaction spendingTx = txMap.get(spentByTransactionHash);
                if (spendingTx == null) {
                    throw new UnreadableWalletException(String.format("Could not connect %s to %s",
                            tx.getHashAsString(), byteStringToHash(spentByTransactionHash)));
                }
                final int spendingIndex = transactionOutput.getSpentByTransactionIndex();
                TransactionInput input = checkNotNull(spendingTx.getInput(spendingIndex), "A spending index does not exist");
                input.connect(output);
            }
        }

        if (txProto.hasConfidence()) {
            Protos.TransactionConfidence confidenceProto = txProto.getConfidence();
            TransactionConfidence confidence = tx.getConfidence();
            readConfidence(tx, confidenceProto, confidence);
        }

        return new WalletTransaction(pool, tx);
    }

    private void readConfidence(Transaction tx, Protos.TransactionConfidence confidenceProto,
                                TransactionConfidence confidence) throws UnreadableWalletException {
        // We are lenient here because tx confidence is not an essential part of the wallet.
        // If the tx has an unknown type of confidence, ignore.
        if (!confidenceProto.hasType()) {
            log.warn("Unknown confidence type for tx {}", tx.getHashAsString());
            return;
        }
        ConfidenceType confidenceType;
        switch (confidenceProto.getType()) {
            case BUILDING: confidenceType = ConfidenceType.BUILDING; break;
            case DEAD: confidenceType = ConfidenceType.DEAD; break;
            case PENDING: confidenceType = ConfidenceType.PENDING; break;
            case UNKNOWN:
                // Fall through.
            default:
                confidenceType = ConfidenceType.UNKNOWN; break;
        }
        confidence.setConfidenceType(confidenceType);
        if (confidenceProto.hasAppearedAtHeight()) {
            if (confidence.getConfidenceType() != ConfidenceType.BUILDING) {
                log.warn("Have appearedAtHeight but not BUILDING for tx {}", tx.getHashAsString());
                return;
            }
            confidence.setAppearedAtChainHeight(confidenceProto.getAppearedAtHeight());
        }
        if (confidenceProto.hasDepth()) {
            if (confidence.getConfidenceType() != ConfidenceType.BUILDING) {
                log.warn("Have depth but not BUILDING for tx {}", tx.getHashAsString());
                return;
            }
            confidence.setDepthInBlocks(confidenceProto.getDepth());
        }
        if (confidenceProto.hasOverridingTransaction()) {
            if (confidence.getConfidenceType() != ConfidenceType.DEAD) {
                log.warn("Have overridingTransaction but not OVERRIDDEN for tx {}", tx.getHashAsString());
                return;
            }
            Transaction overridingTransaction =
                    txMap.get(confidenceProto.getOverridingTransaction());
            if (overridingTransaction == null) {
                log.warn("Have overridingTransaction that is not in wallet for tx {}", tx.getHashAsString());
                return;
            }
            confidence.setOverridingTransaction(overridingTransaction);
        }
        for (Protos.PeerAddress proto : confidenceProto.getBroadcastByList()) {
            InetAddress ip;
            try {
                ip = InetAddress.getByAddress(proto.getIpAddress().toByteArray());
            } catch (UnknownHostException e) {
                throw new UnreadableWalletException("Peer IP address does not have the right length", e);
            }
            int port = proto.getPort();
            PeerAddress address = new PeerAddress(ip, port);
            address.setServices(BigInteger.valueOf(proto.getServices()));
            confidence.markBroadcastBy(address);
        }
        switch (confidenceProto.getSource()) {
            case SOURCE_SELF: confidence.setSource(TransactionConfidence.Source.SELF); break;
            case SOURCE_NETWORK: confidence.setSource(TransactionConfidence.Source.NETWORK); break;
            case SOURCE_UNKNOWN:
                // Fall through.
            default: confidence.setSource(TransactionConfidence.Source.UNKNOWN); break;
        }
    }

}
