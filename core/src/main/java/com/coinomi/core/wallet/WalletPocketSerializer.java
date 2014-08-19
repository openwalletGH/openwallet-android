/**
 * Copyright 2012 Google Inc.
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
import com.coinomi.core.protos.Protos;
import com.google.bitcoin.core.PeerAddress;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.crypto.KeyCrypter;
import com.google.bitcoin.crypto.KeyCrypterScrypt;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.wallet.WalletTransaction;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.util.ListIterator;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * @author Giannis Dzegoutanis
 */
public class WalletPocketSerializer {

    public static Protos.WalletPocket toProtobuf(WalletPocket pocket) {

        Protos.WalletPocket.Builder walletBuilder = Protos.WalletPocket.newBuilder();
        walletBuilder.setNetworkIdentifier(pocket.getCoinType().getId());
        if (pocket.getDescription() != null) {
            walletBuilder.setDescription(pocket.getDescription());
        }

        for (AddressStatus status : pocket.getAddressesStatus()) {
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
                if (confidence.getWorkDone() != null) {
                    confidenceBuilder.setWorkDone(confidence.getWorkDone().longValue());
                }
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
//    public Wallet readWallet(Protos.WalletPocket walletProto, @Nullable KeyCrypter keyCrypter) throws UnreadableWalletException {
//        final String paramsID = walletProto.getNetworkIdentifier();
//        CoinType params = CoinType.fromID(paramsID);
//        if (params == null)
//            throw new UnreadableWalletException("Unknown network parameters ID " + paramsID);
//
//        // Read the scrypt parameters that specify how encryption and decryption is performed.
//        SimpleHDKeyChain chain;
//        if (keyCrypter != null) {
//            chain = SimpleHDKeyChain.fromProtobufEncrypted(params, walletProto.getKeyList(), keyCrypter);
//        } else {
//            chain = SimpleHDKeyChain.fromProtobufUnencrypted(params, walletProto.getKeyList());
//        }
//        Wallet wallet = factory.create(params, chain);
//
//        List<Script> scripts = Lists.newArrayList();
//        for (Protos.Script protoScript : walletProto.getWatchedScriptList()) {
//            try {
//                Script script =
//                        new Script(protoScript.getProgram().toByteArray(),
//                                protoScript.getCreationTimestamp() / 1000);
//                scripts.add(script);
//            } catch (ScriptException e) {
//                throw new UnreadableWalletException("Unparseable script in wallet");
//            }
//        }
//
//        wallet.addWatchedScripts(scripts);
//
//        if (walletProto.hasDescription()) {
//            wallet.setDescription(walletProto.getDescription());
//        }
//
//        // Read all transactions and insert into the txMap.
//        for (Protos.Transaction txProto : walletProto.getTransactionList()) {
//            readTransaction(txProto, wallet.getParams());
//        }
//
//        // Update transaction outputs to point to inputs that spend them
//        for (Protos.Transaction txProto : walletProto.getTransactionList()) {
//            WalletTransaction wtx = connectTransactionOutputs(txProto);
//            wallet.addWalletTransaction(wtx);
//        }
//
//        // Update the lastBlockSeenHash.
//        if (!walletProto.hasLastSeenBlockHash()) {
//            wallet.setLastBlockSeenHash(null);
//        } else {
//            wallet.setLastBlockSeenHash(byteStringToHash(walletProto.getLastSeenBlockHash()));
//        }
//        if (!walletProto.hasLastSeenBlockHeight()) {
//            wallet.setLastBlockSeenHeight(-1);
//        } else {
//            wallet.setLastBlockSeenHeight(walletProto.getLastSeenBlockHeight());
//        }
//        // Will default to zero if not present.
//        wallet.setLastBlockSeenTimeSecs(walletProto.getLastSeenBlockTimeSecs());
//
//        if (walletProto.hasKeyRotationTime()) {
//            wallet.setKeyRotationTime(new Date(walletProto.getKeyRotationTime() * 1000));
//        }
//
//        loadExtensions(wallet, extensions != null ? extensions : new WalletExtension[0], walletProto);
//
//        for (Protos.Tag tag : walletProto.getTagsList()) {
//            wallet.setTag(tag.getTag(), tag.getData());
//        }
//
//        if (walletProto.hasVersion()) {
//            wallet.setVersion(walletProto.getVersion());
//        }
//
//        // Make sure the object can be re-used to read another wallet without corruption.
//        txMap.clear();
//
//        return wallet;
//    }
//
//
//    /**
//     * Returns the loaded protocol buffer from the given byte stream. You normally want
//     * {@link Wallet#loadFromFile(java.io.File)} instead - this method is designed for low level work involving the
//     * wallet file format itself.
//     */
//    public static Protos.WalletPocket parseToProto(InputStream input) throws IOException {
//        return Protos.WalletPocket.parseFrom(input);
//    }

}
