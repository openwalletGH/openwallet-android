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
package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.protos.Protos;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.store.UnreadableWalletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
final public class NxtFamilyWalletProtobufSerializer {
    private static final Logger log = LoggerFactory.getLogger(NxtFamilyWalletProtobufSerializer.class);

    // Used for de-serialization
    protected Map<ByteString, Transaction> txMap = new HashMap<ByteString, Transaction>();

    public static Protos.WalletPocket toProtobuf(NxtFamilyWallet account) {

        Protos.WalletPocket.Builder walletBuilder = Protos.WalletPocket.newBuilder();
        walletBuilder.setNetworkIdentifier(account.getCoinType().getId());
        if (account.getDescription() != null) {
            walletBuilder.setDescription(account.getDescription());
        }
        if (account.getId() != null) {
            walletBuilder.setId(account.getId());
        }

//        for (WalletTransaction wtx : account.getWalletTransactions()) {
//            Protos.Transaction txProto = makeTxProto(wtx);
//            walletBuilder.addTransaction(txProto);
//        }

        walletBuilder.addAllKey(account.serializeKeychainToProtobuf());

        // Populate the lastSeenBlockHash field.
//        if (account.getLastBlockSeenHash() != null) {
//            walletBuilder.setLastSeenBlockHash(hashToByteString(account.getLastBlockSeenHash()));
//            walletBuilder.setLastSeenBlockHeight(account.getLastBlockSeenHeight());
//        }
//        if (account.getLastBlockSeenTimeSecs() > 0) {
//            walletBuilder.setLastSeenBlockTimeSecs(account.getLastBlockSeenTimeSecs());
//        }

        return walletBuilder.build();
    }

//    private static Protos.Transaction makeTxProto(WalletTransaction wtx) {
//        Transaction tx = wtx.getTransaction();
//        Protos.Transaction.Builder txBuilder = Protos.Transaction.newBuilder();
//
//
//        txBuilder.setPool(getProtoPool(wtx))
//                .setHash(hashToByteString(tx.getHash()))
//                .setVersion((int) tx.getVersion());
//
//        if (Networks.isFamily(tx.getParams(), PEERCOIN, NUBITS, REDDCOIN)) {
//            txBuilder.setTime((int) tx.getTime());
//        }
//
//        if (Networks.isFamily(tx.getParams(), NUBITS)) {
//            txBuilder.setTokenId(tx.getTokenId());
//        }
//
//        if (tx.getUpdateTime() != null) {
//            txBuilder.setUpdatedAt(tx.getUpdateTime().getTime());
//        }
//
//        if (tx.getLockTime() > 0) {
//            txBuilder.setLockTime((int)tx.getLockTime());
//        }
//
//        // Handle inputs.
//        for (TransactionInput input : tx.getInputs()) {
//            Protos.TransactionInput.Builder inputBuilder = Protos.TransactionInput.newBuilder()
//                    .setScriptBytes(ByteString.copyFrom(input.getScriptBytes()))
//                    .setTransactionOutPointHash(hashToByteString(input.getOutpoint().getHash()))
//                    .setTransactionOutPointIndex((int) input.getOutpoint().getIndex());
//            if (input.hasSequence())
//                inputBuilder.setSequence((int) input.getSequenceNumber());
//            if (input.getValue() != null)
//                inputBuilder.setValue(input.getValue().value);
//            txBuilder.addTransactionInput(inputBuilder);
//        }
//
//        // Handle outputs.
//        for (TransactionOutput output : tx.getOutputs()) {
//            Protos.TransactionOutput.Builder outputBuilder = Protos.TransactionOutput.newBuilder()
//                    .setScriptBytes(ByteString.copyFrom(output.getScriptBytes()))
//                    .setValue(output.getValue().value);
//            final TransactionInput spentBy = output.getSpentBy();
//            if (spentBy != null) {
//                Sha256Hash spendingHash = spentBy.getParentTransaction().getHash();
//                int spentByTransactionIndex = spentBy.getParentTransaction().getInputs().indexOf(spentBy);
//                outputBuilder.setSpentByTransactionHash(hashToByteString(spendingHash))
//                        .setSpentByTransactionIndex(spentByTransactionIndex);
//            }
//            txBuilder.addTransactionOutput(outputBuilder);
//        }
//
//        // Handle which blocks tx was seen in.
//        final Map<Sha256Hash, Integer> appearsInHashes = tx.getAppearsInHashes();
//        if (appearsInHashes != null) {
//            for (Map.Entry<Sha256Hash, Integer> entry : appearsInHashes.entrySet()) {
//                txBuilder.addBlockHash(hashToByteString(entry.getKey()));
//                txBuilder.addBlockRelativityOffsets(entry.getValue());
//            }
//        }
//
//        if (tx.hasConfidence()) {
//            TransactionConfidence confidence = tx.getConfidence();
//            Protos.TransactionConfidence.Builder confidenceBuilder = Protos.TransactionConfidence.newBuilder();
//            writeConfidence(txBuilder, confidence, confidenceBuilder);
//        }
//
//        return txBuilder.build();
//    }
//
//    private static Protos.Transaction.Pool getProtoPool(WalletTransaction wtx) {
//        switch (wtx.getPool()) {
//            case UNSPENT: return Protos.Transaction.Pool.UNSPENT;
//            case SPENT: return Protos.Transaction.Pool.SPENT;
//            case DEAD: return Protos.Transaction.Pool.DEAD;
//            case PENDING: return Protos.Transaction.Pool.PENDING;
//            default:
//                throw new RuntimeException("Unreachable");
//        }
//    }
//
//    private static void writeConfidence(Protos.Transaction.Builder txBuilder,
//                                        TransactionConfidence confidence,
//                                        Protos.TransactionConfidence.Builder confidenceBuilder) {
//        synchronized (confidence) {
//            confidenceBuilder.setType(Protos.TransactionConfidence.Type.valueOf(confidence.getConfidenceType().getValue()));
//            if (confidence.getConfidenceType() == ConfidenceType.BUILDING) {
//                confidenceBuilder.setAppearedAtHeight(confidence.getAppearedAtChainHeight());
//                confidenceBuilder.setDepth(confidence.getDepthInBlocks());
//            }
//            if (confidence.getConfidenceType() == ConfidenceType.DEAD) {
//                // Copy in the overriding transaction, if available.
//                // (A dead coinbase transaction has no overriding transaction).
//                if (confidence.getOverridingTransaction() != null) {
//                    Sha256Hash overridingHash = confidence.getOverridingTransaction().getHash();
//                    confidenceBuilder.setOverridingTransaction(hashToByteString(overridingHash));
//                }
//            }
//            TransactionConfidence.Source source = confidence.getSource();
//            switch (source) {
//                case SELF: confidenceBuilder.setSource(Protos.TransactionConfidence.Source.SOURCE_SELF); break;
//                case NETWORK: confidenceBuilder.setSource(Protos.TransactionConfidence.Source.SOURCE_NETWORK); break;
//                case UNKNOWN:
//                    // Fall through.
//                default:
//                    confidenceBuilder.setSource(Protos.TransactionConfidence.Source.SOURCE_UNKNOWN); break;
//            }
//        }
//
//        for (ListIterator<PeerAddress> it = confidence.getBroadcastBy(); it.hasNext();) {
//            PeerAddress address = it.next();
//            Protos.PeerAddress proto = Protos.PeerAddress.newBuilder()
//                    .setIpAddress(ByteString.copyFrom(address.getAddr().getAddress()))
//                    .setPort(address.getPort())
//                    .setServices(address.getServices().longValue())
//                    .build();
//            confidenceBuilder.addBroadcastBy(proto);
//        }
//        txBuilder.setConfidence(confidenceBuilder);
//    }

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
    public NxtFamilyWallet readWallet(Protos.WalletPocket walletProto, @Nullable KeyCrypter keyCrypter) throws UnreadableWalletException {
        CoinType coinType;
        try {
            coinType = CoinID.typeFromId(walletProto.getNetworkIdentifier());
        } catch (IllegalArgumentException e) {
            throw new UnreadableWalletException("Unknown network parameters ID " + walletProto.getNetworkIdentifier());
        }

        // Read the scrypt parameters that specify how encryption and decryption is performed.
        NxtFamilyKey rootKey;
        if (keyCrypter != null) {
            rootKey = NxtFamilyKey.fromProtobuf(walletProto.getKeyList(), keyCrypter);
        } else {
            rootKey = NxtFamilyKey.fromProtobuf(walletProto.getKeyList());
        }

        NxtFamilyWallet pocket;
        if (walletProto.hasId()) {
            pocket = new NxtFamilyWallet(walletProto.getId(), rootKey, coinType);
        } else {
            pocket = new NxtFamilyWallet(rootKey, coinType);
        }

        if (walletProto.hasDescription()) {
            pocket.setDescription(walletProto.getDescription());
        }

        // TODO ready transactions? Check com.coinomi.core.wallet WalletPocketProtobufSerializer

        return pocket;
    }
}
