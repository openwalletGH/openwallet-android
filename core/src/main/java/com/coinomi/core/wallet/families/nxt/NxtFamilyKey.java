package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.nxt.Crypto;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.util.KeyUtils;
import com.google.protobuf.ByteString;

import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.wallet.EncryptableKeyChain;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.KeyChainEventListener;
import org.bitcoinj.wallet.RedeemData;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newLinkedList;

/**
 * @author John L. Jegutanis
 */
final public class NxtFamilyKey implements EncryptableKeyChain, KeyBag, Serializable {
    private final DeterministicKey entropy;
    private final byte[] publicKey;

    public NxtFamilyKey(DeterministicKey entropy, @Nullable KeyCrypter keyCrypter,
                        @Nullable KeyParameter key) {
        checkArgument(!entropy.isEncrypted(), "Entropy must not be encrypted");
        this.publicKey = Crypto.getPublicKey(entropy.getPrivKeyBytes());
        // Encrypt entropy if needed
        if (keyCrypter != null && key != null) {
            this.entropy = entropy.encrypt(keyCrypter, key, entropy.getParent());
        } else {
            this.entropy = entropy;
        }
    }

    private NxtFamilyKey(DeterministicKey entropy, byte[] publicKey) {
        this.entropy = entropy;
        this.publicKey = publicKey;

    }

    public boolean isEncrypted() {
        return entropy.isEncrypted();
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public byte[] getPrivateKey() {
        return Crypto.convertToPrivateKey(entropy.getPrivKeyBytes());
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
        throw new RuntimeException("Not implemented");
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubKey(byte[] pubkey) {
        throw new RuntimeException("Not implemented");
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean hasKey(ECKey key) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public List<? extends ECKey> getKeys(KeyPurpose purpose, int numberOfKeys) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public ECKey getKey(KeyPurpose purpose) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void addEventListener(KeyChainEventListener listener) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void addEventListener(KeyChainEventListener listener, Executor executor) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean removeEventListener(KeyChainEventListener listener) {
        throw new RuntimeException("Not implemented");
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Serialization support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public List<org.bitcoinj.wallet.Protos.Key> serializeToProtobuf() {
        throw new RuntimeException("Not implemented. Use toProtobuf() method instead.");
    }

    List<Protos.Key> toProtobuf() {
        LinkedList<Protos.Key> entries = newLinkedList();
        List<Protos.Key.Builder> protos = toEditableProtobuf();
        for (Protos.Key.Builder proto : protos) {
            entries.add(proto.build());
        }
        return entries;
    }

    List<Protos.Key.Builder> toEditableProtobuf() {
        LinkedList<Protos.Key.Builder> entries = newLinkedList();

        // Entropy
        Protos.Key.Builder entropyProto = KeyUtils.serializeKey(entropy);
        entropyProto.setType(Protos.Key.Type.DETERMINISTIC_KEY);
        final Protos.DeterministicKey.Builder detKey = entropyProto.getDeterministicKeyBuilder();
        detKey.setChainCode(ByteString.copyFrom(entropy.getChainCode()));
        for (ChildNumber num : entropy.getPath()) {
            detKey.addPath(num.i());
        }
        entries.add(entropyProto);

        // NTX key
        Protos.Key.Builder publicKeyProto = Protos.Key.newBuilder();
        publicKeyProto.setType(Protos.Key.Type.ORIGINAL);
        publicKeyProto.setPublicKey(ByteString.copyFrom(publicKey));
        entries.add(publicKeyProto);

        return entries;
    }

    /**
     * Returns the key chain found in the given list of keys. Used for unencrypted chains
     */
    public static NxtFamilyKey fromProtobuf(List<Protos.Key> keys) throws UnreadableWalletException {
        return fromProtobuf(keys, null);
    }

    /**
     * Returns the key chain found in the given list of keys.
     */
    public static NxtFamilyKey fromProtobuf(List<Protos.Key> keys, @Nullable KeyCrypter crypter)
            throws UnreadableWalletException {
        if (keys.size() != 2) {
            throw new UnreadableWalletException("Expected 2 keys, NXT secret and Curve25519 " +
                    "pub/priv pair, instead got: " + keys.size());
        }

        Protos.Key entropyProto = keys.get(0);
        DeterministicKey entropyKey = KeyUtils.getDeterministicKey(entropyProto, null, crypter);

        Protos.Key publicKeyProto = keys.get(1);
        if (publicKeyProto.getType() != Protos.Key.Type.ORIGINAL) {
            throw new UnreadableWalletException("Unexpected type for NXT public key: " +
                    publicKeyProto.getType());
        }
        byte[] publicKeyBytes = publicKeyProto.getPublicKey().toByteArray();

        return new NxtFamilyKey(entropyKey, publicKeyBytes);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Encryption support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public NxtFamilyKey toEncrypted(CharSequence password) {
        checkNotNull(password, "Attempt to encrypt with a null password.");
        checkArgument(password.length() > 0, "Attempt to encrypt with an empty password.");
        checkState(!entropy.isEncrypted(), "Attempt to encrypt a key that is already encrypted.");
        KeyCrypter scrypt = new KeyCrypterScrypt();
        KeyParameter derivedKey = scrypt.deriveKey(password);
        return toEncrypted(scrypt, derivedKey);
    }

    @Override
    public NxtFamilyKey toEncrypted(KeyCrypter keyCrypter, KeyParameter aesKey) {
        checkState(!entropy.isEncrypted(), "Attempt to encrypt a key that is already encrypted.");
        return new NxtFamilyKey(entropy.encrypt(keyCrypter, aesKey, null), publicKey);
    }

    @Override
    public NxtFamilyKey toDecrypted(CharSequence password) {
        checkNotNull(password, "Attempt to decrypt with a null password.");
        checkArgument(password.length() > 0, "Attempt to decrypt with an empty password.");
        KeyCrypter crypter = getKeyCrypter();
        checkState(crypter != null, "Chain not encrypted");
        KeyParameter derivedKey = crypter.deriveKey(password);
        return toDecrypted(derivedKey);
    }

    @Override
    public NxtFamilyKey toDecrypted(KeyParameter aesKey) {
        checkState(isEncrypted(), "Key is not encrypted");
        checkNotNull(getKeyCrypter(), "Key chain not encrypted");
        DeterministicKey entropyDecrypted = entropy.decrypt(getKeyCrypter(), aesKey);
        return new NxtFamilyKey(entropyDecrypted, publicKey);
    }

    @Override
    public boolean checkPassword(CharSequence password) {
        checkNotNull(password, "Password is null");
        checkState(getKeyCrypter() != null, "Key chain not encrypted");
        return checkAESKey(getKeyCrypter().deriveKey(password));
    }

    @Override
    public boolean checkAESKey(KeyParameter aesKey) {
        checkNotNull(aesKey, "Cannot check null KeyParameter");
        checkNotNull(getKeyCrypter(), "Key not encrypted");
        try {
            return Arrays.equals(publicKey,
                    Crypto.getPublicKey(entropy.decrypt(aesKey).getPrivKeyBytes()));
        } catch (KeyCrypterException e) {
            return false;
        }
    }

    @Nullable
    @Override
    public KeyCrypter getKeyCrypter() {
        return entropy.getKeyCrypter();
    }

    @Override
    public int numKeys() {
        return 1;
    }

    @Override
    public long getEarliestKeyCreationTime() {
        return entropy.getCreationTimeSeconds();
    }

    @Override
    public int numBloomFilterEntries() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public BloomFilter getFilter(int size, double falsePositiveRate, long tweak) {
        throw new RuntimeException("Not implemented");
    }
}