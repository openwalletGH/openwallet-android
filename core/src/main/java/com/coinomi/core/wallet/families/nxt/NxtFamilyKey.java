package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.nxt.Crypto;
import com.coinomi.core.coins.nxt.NxtException;

import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.EncryptableItem;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.EncryptableKeyChain;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.KeyChainEventListener;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.RedeemData;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import static com.coinomi.core.CoreUtils.bytesToMnemonic;
import static com.coinomi.core.CoreUtils.bytesToMnemonicString;
import static com.coinomi.core.CoreUtils.getMnemonicToString;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
public class NxtFamilyKey implements EncryptableKeyChain, KeyBag, EncryptableItem, Serializable {
    private final byte[] entropy;
    private final byte[] privateKey;
    private final byte[] publicKey;
    protected KeyCrypter keyCrypter;
    protected EncryptedData encryptedPrivateKey;
    protected EncryptedData encryptedEntropy;

    public NxtFamilyKey(DeterministicKey key) {
        entropy = key.getPrivKeyBytes();
        String secret = getMnemonic();
        privateKey = Crypto.getPrivateKey(secret);
        publicKey = Crypto.getPublicKey(secret);
    }

//    public DeterministicKey(ImmutableList<ChildNumber> childNumberPath,
//                            byte[] chainCode,
//                            KeyCrypter crypter, ECPoint pub, EncryptedData priv, @Nullable DeterministicKey parent) {
//        this(childNumberPath, chainCode, pub, null, parent);
//        this.encryptedPrivateKey = checkNotNull(priv);
//        this.keyCrypter = checkNotNull(crypter);
//    }

    public String getMnemonic() {
        return bytesToMnemonicString(entropy);
    }

    /**
     * A deterministic key is considered to be encrypted if it has access to encrypted private key bytes, OR if its
     * parent does. The reason is because the parent would be encrypted under the same key and this key knows how to
     * rederive its own private key bytes from the parent, if needed.
     */
    @Override
    public boolean isEncrypted() {
        return privateKey == null;
    }

    @Nullable
    @Override
    public byte[] getSecretBytes() {
        return getPrivKeyBytes();
    }

    @Nullable
    @Override
    public EncryptedData getEncryptedData() {
        return null;
    }

    @Override
    public Protos.Wallet.EncryptionType getEncryptionType() {
        return null;
    }

    @Override
    public long getCreationTimeSeconds() {
        return 0;
    }

    public byte[] getPrivKeyBytes() {
        if (privateKey == null)
            throw new ECKey.MissingPrivateKeyException();
        return privateKey;
    }

    public byte[] getPublicKeyBytes() {
        return publicKey;
    }

    @Override
    public EncryptableKeyChain toEncrypted(CharSequence password) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public EncryptableKeyChain toEncrypted(KeyCrypter keyCrypter, KeyParameter aesKey) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public EncryptableKeyChain toDecrypted(CharSequence password) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public EncryptableKeyChain toDecrypted(KeyParameter aesKey) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean checkPassword(CharSequence password) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean checkAESKey(KeyParameter aesKey) {
        throw new RuntimeException("Not implemented");
    }

    @Nullable
    @Override
    public KeyCrypter getKeyCrypter() {
        if (keyCrypter != null)
            return keyCrypter;
        else
            return null;
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
    public List<Protos.Key> serializeToProtobuf() {
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

    @Override
    public int numKeys() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int numBloomFilterEntries() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long getEarliestKeyCreationTime() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public BloomFilter getFilter(int size, double falsePositiveRate, long tweak) {
        throw new RuntimeException("Not implemented");
    }
}
