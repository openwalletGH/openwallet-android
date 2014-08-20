package com.coinomi.core.wallet;

import com.coinomi.core.Constants;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.wallet.Wallet;
import com.google.bitcoin.store.UnreadableWalletException;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;

/**
 * @author Giannis Dzegoutanis
 */
public class WalletProtobufSerializer {
    /**
     * <p>Parses a wallet from the given stream, using the provided Wallet instance to load data into. This is primarily
     * used when you want to register extensions. Data in the proto will be added into the wallet where applicable and
     * overwrite where not.</p>
     *
     * <p>A wallet can be unreadable for various reasons, such as inability to open the file, corrupt data, internally
     * inconsistent data, a wallet extension marked as mandatory that cannot be handled and so on. You should always
     * handle {@link UnreadableWalletException} and communicate failure to the user in an appropriate manner.</p>
     *
     * @throws UnreadableWalletException thrown in various error conditions (see description).
     */
    public Wallet readWallet(InputStream input) throws UnreadableWalletException {
            return null;
//        try {
//            Protos.Wallet walletProto = parseToProto(input);
//            return readWallet(walletProto);
//        } catch (IOException e) {
//            throw new UnreadableWalletException("Could not parse input stream to protobuf", e);
//        }
    }
//
//    /**
//     * <p>Loads wallet data from the given protocol buffer and inserts it into the given Wallet object. This is primarily
//     * useful when you wish to pre-register extension objects. Note that if loading fails the provided Wallet object
//     * may be in an indeterminate state and should be thrown away.</p>
//     *
//     * <p>A wallet can be unreadable for various reasons, such as inability to open the file, corrupt data, internally
//     * inconsistent data, a wallet extension marked as mandatory that cannot be handled and so on. You should always
//     * handle {@link UnreadableWalletException} and communicate failure to the user in an appropriate manner.</p>
//     *
//     * @throws UnreadableWalletException thrown in various error conditions (see description).
//     */
//    public Wallet readWallet(Protos.Wallet walletProto) throws UnreadableWalletException {
//        if (walletProto.getVersion() > 1)
//            throw new UnreadableWalletException.FutureVersion();
//
////        required Key master_key = 2;
////
////        optional EncryptionType encryption_type = 3 [default=UNENCRYPTED];
////        optional ScryptParameters encryption_parameters = 4;
////
////        repeated WalletPocket pockets = 5;
//
//        walletProto.getMasterKey()
//
//        // Read the scrypt parameters that specify how encryption and decryption is performed.
//        KeyChainGroup chain;
//        if (walletProto.hasEncryptionParameters()) {
//            Protos.ScryptParameters encryptionParameters = walletProto.getEncryptionParameters();
//            final KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt(encryptionParameters);
//            chain = KeyChainGroup.fromProtobufEncrypted(params, walletProto.getKeyList(), keyCrypter);
//        } else {
//            chain = KeyChainGroup.fromProtobufUnencrypted(params, walletProto.getKeyList());
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
//    private void loadExtensions(Wallet wallet, WalletExtension[] extensionsList, Protos.Wallet walletProto) throws UnreadableWalletException {
//        final Map<String, WalletExtension> extensions = new HashMap<String, WalletExtension>();
//        for (WalletExtension e : extensionsList)
//            extensions.put(e.getWalletExtensionID(), e);
//        // The Wallet object, if subclassed, might have added some extensions to itself already. In that case, don't
//        // expect them to be passed in, just fetch them here and don't re-add.
//        extensions.putAll(wallet.getExtensions());
//        for (Protos.Extension extProto : walletProto.getExtensionList()) {
//            String id = extProto.getId();
//            WalletExtension extension = extensions.get(id);
//            if (extension == null) {
//                if (extProto.getMandatory()) {
//                    if (requireMandatoryExtensions)
//                        throw new UnreadableWalletException("Unknown mandatory extension in wallet: " + id);
//                    else
//                        log.error("Unknown extension in wallet {}, ignoring", id);
//                }
//            } else {
//                log.info("Loading wallet extension {}", id);
//                try {
//                    extension.deserializeWalletExtension(wallet, extProto.getData().toByteArray());
//                    wallet.addOrGetExistingExtension(extension);
//                } catch (Exception e) {
//                    if (extProto.getMandatory() && requireMandatoryExtensions)
//                        throw new UnreadableWalletException("Could not parse mandatory extension in wallet: " + id);
//                    else
//                        log.error("Error whilst reading extension {}, ignoring", id, e);
//                }
//            }
//        }
//    }
//
//    /**
//     * Returns the loaded protocol buffer from the given byte stream. You normally want
//     * {@link Wallet#loadFromFile(java.io.File)} instead - this method is designed for low level work involving the
//     * wallet file format itself.
//     */
//    public static Protos.Wallet parseToProto(InputStream input) throws IOException {
//        return Protos.Wallet.parseFrom(input);
//    }
}
