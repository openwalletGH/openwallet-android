package com.coinomi.core.wallet;

import com.coinomi.core.protos.Protos;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.EncryptableItem;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.BasicKeyChain;

import com.coinomi.core.util.KeyUtils;
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
            Protos.Key.Builder protoKey = KeyUtils.serializeKey(ecKey);
            result.put(ecKey, protoKey);
        }
        return result;
    }
}
