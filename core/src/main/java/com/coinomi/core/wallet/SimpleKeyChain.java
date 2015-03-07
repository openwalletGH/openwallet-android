package com.coinomi.core.wallet;

import com.coinomi.core.protos.Protos;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.EncryptableItem;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.BasicKeyChain;
import com.google.protobuf.ByteString;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class SimpleKeyChain extends BasicKeyChain {

    public SimpleKeyChain(KeyCrypter crypter) {
        super(crypter);
    }

    public SimpleKeyChain() {
        super();
    }

    Map<ECKey, Protos.Key.Builder> toEditableProtobufs() {
        Map<ECKey, Protos.Key.Builder> result = new LinkedHashMap<ECKey, Protos.Key.Builder>();
        for (ECKey ecKey : getKeys()) {
            Protos.Key.Builder protoKey = serializeKey(ecKey);
            result.put(ecKey, protoKey);
        }
        return result;
    }

    /*package*/ static Protos.Key.Builder serializeEncryptableItem(EncryptableItem item) {
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

    /*package*/ static Protos.Key.Builder serializeKey(ECKey key) {
        Protos.Key.Builder protoKey = serializeEncryptableItem(key);
        protoKey.setPublicKey(ByteString.copyFrom(key.getPubKey()));
        return protoKey;
    }
}
