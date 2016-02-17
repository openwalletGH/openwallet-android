package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.families.BitFamily;
import com.coinomi.core.coins.families.NxtFamily;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.util.KeyUtils;
import com.coinomi.core.wallet.families.bitcoin.BitTransaction;
import com.coinomi.core.wallet.families.bitcoin.OutPointOutput;
import com.coinomi.core.wallet.families.nxt.NxtFamilyWallet;
import com.coinomi.core.wallet.families.nxt.NxtFamilyWalletProtobufSerializer;
import com.google.common.base.Splitter;
import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.wallet.DeterministicSeed;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING;

/**
 * @author John L. Jegutanis
 */
public class WalletProtobufSerializer {

    /**
     * Formats the given wallet (transactions and keys) to the given output stream in protocol buffer format.<p>
     *
     * Equivalent to <tt>walletToProto(wallet).writeTo(output);</tt>
     */
    public static void writeWallet(Wallet wallet, OutputStream output) throws IOException {
        Protos.Wallet walletProto = toProtobuf(wallet);
        walletProto.writeTo(output);
    }

    /**
     * Returns the given wallet formatted as text. The text format is that used by protocol buffers and although it
     * can also be parsed using {@link TextFormat#merge(CharSequence, com.google.protobuf.Message.Builder)},
     * it is designed more for debugging than storage. It is not well specified and wallets are largely binary data
     * structures anyway, consisting as they do of keys (large random numbers) and
     * {@link org.bitcoinj.core.Transaction}s which also mostly contain keys and hashes.
     */
    public static String walletToText(Wallet wallet) {
        Protos.Wallet walletProto = toProtobuf(wallet);
        return TextFormat.printToString(walletProto);
    }

    /**
     * Converts the given wallet to the object representation of the protocol buffers. This can be modified, or
     * additional data fields set, before serialization takes place.
     */
    public static Protos.Wallet toProtobuf(Wallet wallet) {
        Protos.Wallet.Builder walletBuilder = Protos.Wallet.newBuilder();

        // Populate the wallet version.
        walletBuilder.setVersion(wallet.getVersion());

        // Set the seed if exists
        if (wallet.getSeed() != null) {
            Protos.Key.Builder mnemonicEntry = KeyUtils.serializeEncryptableItem(wallet.getSeed());
            mnemonicEntry.setType(Protos.Key.Type.DETERMINISTIC_MNEMONIC);
            walletBuilder.setSeed(mnemonicEntry.build());
        }

        // Set the master key
        walletBuilder.setMasterKey(getMasterKeyProto(wallet));

        // Populate the scrypt parameters.
        KeyCrypter keyCrypter = wallet.getKeyCrypter();
        if (keyCrypter == null) {
            // The wallet is unencrypted.
            walletBuilder.setEncryptionType(Protos.Wallet.EncryptionType.UNENCRYPTED);
        } else {
            // The wallet is encrypted.
            if (keyCrypter instanceof KeyCrypterScrypt) {
                KeyCrypterScrypt keyCrypterScrypt = (KeyCrypterScrypt) keyCrypter;
                walletBuilder.setEncryptionType(Protos.Wallet.EncryptionType.ENCRYPTED_SCRYPT_AES);

                // Bitcoinj format to our native protobuf
                Protos.ScryptParameters.Builder encParamBuilder = Protos.ScryptParameters.newBuilder();
                encParamBuilder.setSalt(keyCrypterScrypt.getScryptParameters().getSalt());
                encParamBuilder.setR(keyCrypterScrypt.getScryptParameters().getR());
                encParamBuilder.setP(keyCrypterScrypt.getScryptParameters().getP());
                encParamBuilder.setN(keyCrypterScrypt.getScryptParameters().getN());

                walletBuilder.setEncryptionParameters(encParamBuilder);
            } else {
                // Some other form of encryption has been specified that we do not know how to persist.
                throw new RuntimeException("The wallet has encryption of type '" +
                        keyCrypter.getClass().toString() + "' but this WalletProtobufSerializer " +
                        "does not know how to persist this.");
            }
        }

        // Add serialized pockets
        for (WalletAccount account : wallet.getAllAccounts()) {
            Protos.WalletPocket pocketProto;
            if (account instanceof WalletPocketHD) {
                pocketProto = WalletPocketProtobufSerializer.toProtobuf((WalletPocketHD) account);
            } else if (account instanceof NxtFamilyWallet) {
                pocketProto = NxtFamilyWalletProtobufSerializer.toProtobuf((NxtFamilyWallet) account);
            } else {
                throw new RuntimeException("Implement serialization for: " + account.getClass());
            }
            walletBuilder.addPockets(pocketProto);
        }

        return walletBuilder.build();
    }

    private static Protos.Key getMasterKeyProto(Wallet wallet) {
        DeterministicKey key = wallet.getMasterKey();
        Protos.Key.Builder proto = KeyUtils.serializeKey(key);
        proto.setType(Protos.Key.Type.DETERMINISTIC_KEY);
        final Protos.DeterministicKey.Builder detKey = proto.getDeterministicKeyBuilder();
        detKey.setChainCode(ByteString.copyFrom(key.getChainCode()));
        for (ChildNumber num : key.getPath()) {
            detKey.addPath(num.i());
        }
        return proto.build();
    }


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
    public static Wallet readWallet(InputStream input) throws UnreadableWalletException {
        try {
            Protos.Wallet walletProto = parseToProto(input);
            return readWallet(walletProto);
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not parse input stream to protobuf", e);
        }
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
    public static Wallet readWallet(Protos.Wallet walletProto) throws UnreadableWalletException {
        if (walletProto.getVersion() > 3)
            throw new UnreadableWalletException.FutureVersion();

        walletProto = applyProtoUpdates(walletProto);

        // Check if wallet is encrypted
        final KeyCrypter crypter = getKeyCrypter(walletProto);

        DeterministicSeed seed = null;
        if (walletProto.hasSeed()) {
            Protos.Key key = walletProto.getSeed();

            if (key.hasSecretBytes()) {
                List<String> mnemonic = Splitter.on(" ").splitToList(key.getSecretBytes().toStringUtf8());
                seed = new DeterministicSeed(new byte[16], mnemonic, 0);
            } else if (key.hasEncryptedData()) {
                EncryptedData data = new EncryptedData(key.getEncryptedData().getInitialisationVector().toByteArray(),
                        key.getEncryptedData().getEncryptedPrivateKey().toByteArray());
                seed = new DeterministicSeed(data, null, 0);
            } else {
                throw new UnreadableWalletException("Malformed key proto: " + key.toString());
            }
        }

        DeterministicKey masterKey =
                KeyUtils.getDeterministicKey(walletProto.getMasterKey(), null, crypter);

        Wallet wallet = new Wallet(masterKey, seed);

        if (walletProto.hasVersion()) {
            wallet.setVersion(walletProto.getVersion());
        }

        WalletPocketProtobufSerializer pocketSerializer = new WalletPocketProtobufSerializer();
        NxtFamilyWalletProtobufSerializer nxtPocketSerializer = new NxtFamilyWalletProtobufSerializer();
        for (Protos.WalletPocket pocketProto : walletProto.getPocketsList()) {
            CoinType type = getType(pocketProto);
            AbstractWallet pocket;

            if (type instanceof BitFamily) {
                pocket = pocketSerializer.readWallet(pocketProto, crypter);
            } else if (type instanceof NxtFamily) {
                pocket = nxtPocketSerializer.readWallet(pocketProto, crypter);
            } else {
                throw new UnreadableWalletException("Unsupported type " + type);
            }

            wallet.addAccount(pocket);
        }

        applyWalletUpdates(wallet);

        return wallet;
    }

    private static Protos.Wallet applyProtoUpdates(Protos.Wallet walletProto) {
        if (walletProto.getVersion() < 2) {
            walletProto = updateV1toV2Proto(walletProto);
        }

        if (walletProto.getVersion() < 3) {
            walletProto = updateV2toV3Proto(walletProto);
        }
        return walletProto;
    }

    private static void applyWalletUpdates(Wallet wallet) {
        if (wallet.getVersion() < 2) {
            updateV1toV2(wallet);
        }

        if (wallet.getVersion() < 3) {
            updateV2toV3(wallet);
        }
    }

    private static CoinType getType(Protos.WalletPocket proto) throws UnreadableWalletException {
        try {
            return CoinID.typeFromId(proto.getNetworkIdentifier());
        } catch (IllegalArgumentException e) {
            throw new UnreadableWalletException("Unknown network parameters ID " +
                    proto.getNetworkIdentifier());
        }
    }

    private static KeyCrypter getKeyCrypter(Protos.Wallet walletProto) {
        KeyCrypter crypter;
        if (walletProto.hasEncryptionType()) {
            if (walletProto.getEncryptionType() == Protos.Wallet.EncryptionType.ENCRYPTED_SCRYPT_AES) {
                checkState(walletProto.hasEncryptionParameters(), "Encryption parameters are missing");

                Protos.ScryptParameters encryptionParameters = walletProto.getEncryptionParameters();
                org.bitcoinj.wallet.Protos.ScryptParameters.Builder bitcoinjCrypter =
                        org.bitcoinj.wallet.Protos.ScryptParameters.newBuilder();
                bitcoinjCrypter.setSalt(encryptionParameters.getSalt());
                bitcoinjCrypter.setN(encryptionParameters.getN());
                bitcoinjCrypter.setP(encryptionParameters.getP());
                bitcoinjCrypter.setR(encryptionParameters.getR());

                crypter = new KeyCrypterScrypt(bitcoinjCrypter.build());
            }
            else if (walletProto.getEncryptionType() == Protos.Wallet.EncryptionType.UNENCRYPTED) {
                crypter = null;
            }
            else {
                throw new KeyCrypterException("Unsupported encryption: " + walletProto.getEncryptionType().toString());
            }
        }
        else {
            crypter = null;
        }

        return crypter;
    }


    /**
     * Returns the loaded protocol buffer from the given byte stream. You normally want
     * {@link Wallet#loadFromFile(java.io.File)} instead - this method is designed for low level work involving the
     * wallet file format itself.
     */
    public static Protos.Wallet parseToProto(InputStream input) throws IOException {
        return Protos.Wallet.parseFrom(input);
    }


    private static Protos.Wallet updateV2toV3Proto(Protos.Wallet walletProto) {
        checkState(walletProto.getVersion() < 3, "Can update only from version < 3");
        Protos.Wallet.Builder b = walletProto.toBuilder();
        for (int i = 0; i < b.getPocketsCount(); i++) {
            Protos.WalletPocket.Builder account = b.getPocketsBuilder(i);
            // pre v2 wallets were saving the coin name in the description
            account.clearDescription();
        }
        return b.build();
    }

    private static void updateV2toV3(Wallet wallet) {
        checkState(wallet.getVersion() < 3, "Can update only from version < 3");
        wallet.setVersion(3);
    }

    private static Protos.Wallet updateV1toV2Proto(Protos.Wallet walletProto) {
        checkState(walletProto.getVersion() < 2, "Can update only from version < 2");
        // Purge blockchain data if wallet is bigger than 2mb
        boolean purgeBlockchain = walletProto.getSerializedSize() > 200000;
        Protos.Wallet.Builder b = walletProto.toBuilder();
        for (int i = 0; i < b.getPocketsCount(); i++) {
            Protos.WalletPocket.Builder account = b.getPocketsBuilder(i);
            // Purge blockchain data if needed
            if (purgeBlockchain) {
                account.clearAddressStatus();
                account.clearLastSeenBlockHash();
                account.clearLastSeenBlockHeight();
                account.clearLastSeenBlockTimeSecs();
                account.clearTransaction();
            }
            // Update coin type ids
            if (account.getNetworkIdentifier().equals("dogecoindark.main")) {
                account.setNetworkIdentifier("verge.main");
                b.setPockets(i, account);
            }
            if (account.getNetworkIdentifier().equals("darkcoin.main")) {
                account.setNetworkIdentifier("dash.main");
                b.setPockets(i, account);
            }
        }
        return b.build();
    }

    private static void updateV1toV2(Wallet wallet) {
        checkState(wallet.getVersion() < 2, "Can update only from version < 2");
        wallet.setVersion(2);
        for (WalletAccount walletAccount : wallet.getAllAccounts()) {
            if (walletAccount instanceof WalletPocketHD) {
                WalletPocketHD account = (WalletPocketHD) walletAccount;
                // Force resync
                account.addressesStatus.clear();
                // Gather hashes to trim them later
                Set<Sha256Hash> txHashes = new HashSet<>(account.rawTransactions.size());
                // Reconstruct UTXO set
                for (BitTransaction tx : account.rawTransactions.values()) {
                    txHashes.add(tx.getHash());
                    for (TransactionOutput txo : tx.getOutputs()) {
                        if (txo.isAvailableForSpending() && txo.isMineOrWatched(account)) {
                            OutPointOutput utxo = new OutPointOutput(tx, txo.getIndex());
                            if (tx.getConfidenceType() == BUILDING) {
                                utxo.setAppearedAtChainHeight(tx.getAppearedAtChainHeight());
                                utxo.setDepthInBlocks(tx.getDepthInBlocks());
                            }
                            account.unspentOutputs.put(utxo.getOutPoint(), utxo);
                        }
                    }
                }
                // Trim transactions
                for (Sha256Hash txHash : txHashes) {
                    account.trimTransactionIfNeeded(txHash);
                }
            }
        }
    }
}
