package com.coinomi.core.util;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.protos.Protos;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.EncryptableItem;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.LazyECPoint;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class KeyUtils {

    public static Protos.Key.Builder serializeEncryptableItem(EncryptableItem item) {
        Protos.Key.Builder proto = Protos.Key.newBuilder();
        if (item.isEncrypted() && item.getEncryptedData() != null) {
            // The encrypted data can be missing for an "encrypted" key in the case of a deterministic wallet for
            // which the leaf keys chain to an encrypted parent and rederive their private keys on the fly. In that
            // case the caller in DeterministicKeyChain will take care of setting the type.
            EncryptedData data = item.getEncryptedData();
            proto.getEncryptedDataBuilder()
                    .setEncryptedPrivateKey(ByteString.copyFrom(data.encryptedBytes))
                    .setInitialisationVector(ByteString.copyFrom(data.initialisationVector));
            // We don't allow mixing of encryption types at the moment.
            checkState(item.getEncryptionType() == org.bitcoinj.wallet.Protos.Wallet.EncryptionType.ENCRYPTED_SCRYPT_AES, "We don't allow mixing of encryption types at the moment");
            proto.setType(Protos.Key.Type.ENCRYPTED_SCRYPT_AES);
        } else {
            final byte[] secret = item.getSecretBytes();
            // The secret might be missing in the case of a watching wallet, or a key for which the private key
            // is expected to be rederived on the fly from its parent.
            if (secret != null)
                proto.setSecretBytes(ByteString.copyFrom(secret));
            proto.setType(Protos.Key.Type.ORIGINAL);
        }
        return proto;
    }


    public static Protos.Key.Builder serializeKey(ECKey key) {
        Protos.Key.Builder protoKey = serializeEncryptableItem(key);
        protoKey.setPublicKey(ByteString.copyFrom(key.getPubKey()));
        return protoKey;
    }

    public static DeterministicKey getDeterministicKey(Protos.Key key,
                                                       @Nullable DeterministicKey parent,
                                                       @Nullable KeyCrypter crypter) {
        checkState(key.getType() == Protos.Key.Type.DETERMINISTIC_KEY, "Key protocol buffer must " +
                "have be a deterministic key type");
        checkState(key.hasDeterministicKey(), "Deterministic key missing extra data.");

        // Deserialize the path through the tree.
        final ImmutableList<ChildNumber> immutablePath = getKeyProtoPath(key);
        // Deserialize the public key.
        LazyECPoint pubkey = new LazyECPoint(ECKey.CURVE.getCurve(), key.getPublicKey().toByteArray());
        // Deserialize the chain code.
        byte[] chainCode = key.getDeterministicKey().getChainCode().toByteArray();

        DeterministicKey detkey;
        if (key.hasSecretBytes()) {
            // Not encrypted: private key is available.
            final BigInteger priv = new BigInteger(1, key.getSecretBytes().toByteArray());
            detkey = new DeterministicKey(immutablePath, chainCode, pubkey, priv, parent);
        } else {
            if (key.hasEncryptedData()) {
                Protos.EncryptedData proto = key.getEncryptedData();
                EncryptedData data = new EncryptedData(proto.getInitialisationVector().toByteArray(),
                        proto.getEncryptedPrivateKey().toByteArray());
                checkNotNull(crypter, "Encountered an encrypted key but no key crypter provided");
                detkey = new DeterministicKey(immutablePath, chainCode, crypter, pubkey, data, parent);
            } else {
                // No secret key bytes and key is not encrypted: either a watching key or private key bytes
                // will be rederived on the fly from the parent.
//                checkNotNull(parent, "Watching keys are not supported at the moment.");
                detkey = new DeterministicKey(immutablePath, chainCode, pubkey, null, parent);
            }
        }
        return detkey;
    }

    public static ImmutableList<ChildNumber> getKeyProtoPath(Protos.Key key) {
        ImmutableList.Builder<ChildNumber> pathBuilder = ImmutableList.builder();
        for (int i : key.getDeterministicKey().getPathList()) {
            pathBuilder.add(new ChildNumber(i));
        }
        return pathBuilder.build();
    }

    public static String getPublicKeyId(CoinType type, byte[] publicKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(type.getId().getBytes());
            byte[] hash = sha256.digest(publicKey);
            return Utils.HEX.encode(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }
}
