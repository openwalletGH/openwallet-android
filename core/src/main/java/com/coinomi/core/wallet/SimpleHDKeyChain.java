package com.coinomi.core.wallet;


import com.coinomi.core.protos.Protos;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.EncryptableKeyChain;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.KeyChainEventListener;
import org.bitcoinj.wallet.RedeemData;

import com.coinomi.core.util.KeyUtils;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.digests.RIPEMD160Digest;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newLinkedList;

/**
 * @author John L. Jegutanis
 * @author 2013 The bitcoinj developers.
 */


public class SimpleHDKeyChain implements EncryptableKeyChain, KeyBag {
    private static final Logger log = LoggerFactory.getLogger(SimpleHDKeyChain.class);

    public static final int LOOKAHEAD = 20; // BIP 44

    private final ReentrantLock lock = Threading.lock("KeyChain");

    private DeterministicHierarchy hierarchy;
    private DeterministicKey rootKey;

    // Paths through the key tree. External keys are ones that are communicated to other parties. Internal keys are
    // keys created for change addresses, coinbases, mixing, etc - anything that isn't communicated. The distinction
    // is somewhat arbitrary but can be useful for audits.
    public static final ChildNumber EXTERNAL_PATH_NUM = ChildNumber.ZERO;
    public static final ChildNumber INTERNAL_PATH_NUM = ChildNumber.ONE;
    public static final ImmutableList<ChildNumber> EXTERNAL_PATH = ImmutableList.of(EXTERNAL_PATH_NUM);
    public static final ImmutableList<ChildNumber> INTERNAL_PATH = ImmutableList.of(INTERNAL_PATH_NUM);

    // We try to ensure we have at least this many keys ready and waiting to be handed out via getKey().
    // See docs for getLookaheadSize() for more info on what this is for. The -1 value means it hasn't been calculated
    // yet. For new chains it's set to whatever the default is, unless overridden by setLookaheadSize. For deserialized
    // chains, it will be calculated on demand from the number of loaded keys.
    private static final int LAZY_CALCULATE_LOOKAHEAD = -1;
    private int lookaheadSize = LOOKAHEAD;
    // The lookahead threshold causes us to batch up creation of new keys to minimize the frequency of Bloom filter
    // regenerations, which are expensive and will (in future) trigger chain download stalls/retries. One third
    // is an efficiency tradeoff.
    private int lookaheadThreshold = calcDefaultLookaheadThreshold();

    private int calcDefaultLookaheadThreshold() {
        return lookaheadSize / 3;
    }

    // The parent keys for external keys (handed out to other people) and internal keys (used for change addresses).
    private DeterministicKey externalKey, internalKey;
    // How many keys on each path have actually been used. This may be fewer than the number that have been deserialized
    // or held in memory, because of the lookahead zone.
    private int issuedExternalKeys, issuedInternalKeys;

    // We simplify by wrapping a basic key chain and that way we get some functionality like key lookup and event
    // listeners "for free". All keys in the key tree appear here, even if they aren't meant to be used for receiving
    // money.
    private final SimpleKeyChain simpleKeyChain;

    /**
     * Creates a deterministic key chain that watches the given (public only) root key. You can use this to calculate
     * balances and generally follow along, but spending is not possible with such a chain. Currently you can't use
     * this method to watch an arbitrary fragment of some other tree, this limitation may be removed in future.
     */
    public SimpleHDKeyChain(DeterministicKey rootkey) {
        simpleKeyChain = new SimpleKeyChain();
        initializeHierarchyUnencrypted(rootkey);
    }

    SimpleHDKeyChain(DeterministicKey rootkey, @Nullable KeyCrypter crypter) {
        this.rootKey = rootkey;
        simpleKeyChain = new SimpleKeyChain(crypter);
        if (!rootkey.isEncrypted()) {
            initializeHierarchyUnencrypted(rootKey);
        }
        // Else...
        // We can't initialize ourselves with just an encrypted seed, so we expected deserialization code to do the
        // rest of the setup (loading the root key).
    }

    SimpleHDKeyChain(DeterministicKey rootkey, @Nullable KeyCrypter crypter,
                     @Nullable KeyParameter key) {
        simpleKeyChain = new SimpleKeyChain(crypter);
        if (crypter != null && !rootkey.isEncrypted()) {
            this.rootKey = rootkey.encrypt(crypter, key, null);
        } else {
            this.rootKey = rootkey;
        }

        initializeHierarchyUnencrypted(rootKey);
    }

    // For use in encryption.
    private SimpleHDKeyChain(KeyCrypter crypter, KeyParameter aesKey, SimpleHDKeyChain chain) {
        checkArgument(!chain.rootKey.isEncrypted(), "Chain already encrypted");

        this.issuedExternalKeys = chain.issuedExternalKeys;
        this.issuedInternalKeys = chain.issuedInternalKeys;

        this.lookaheadSize = chain.lookaheadSize;
        this.lookaheadThreshold = chain.lookaheadThreshold;

        simpleKeyChain = new SimpleKeyChain(crypter);
        // The first number is the "account number" but we don't use that feature.
        rootKey = chain.rootKey.encrypt(crypter, aesKey, null);
        hierarchy = new DeterministicHierarchy(rootKey);
        simpleKeyChain.importKey(rootKey);

        externalKey = encryptNonLeaf(aesKey, chain, rootKey, EXTERNAL_PATH);
        internalKey = encryptNonLeaf(aesKey, chain, rootKey, INTERNAL_PATH);

        // Now copy the (pubkey only) leaf keys across to avoid rederiving them. The private key bytes are missing
        // anyway so there's nothing to encrypt.
        for (ECKey eckey : chain.simpleKeyChain.getKeys()) {
            DeterministicKey key = (DeterministicKey) eckey;
            if (!isLeaf(key)) continue; // Not a leaf key.
            DeterministicKey parent = hierarchy.get(checkNotNull(key.getParent(), "Key has no parent").getPath(), false, false);
            // Clone the key to the new encrypted hierarchy.
            key = new DeterministicKey(key.getPubOnly(), parent);
            hierarchy.putKey(key);
            simpleKeyChain.importKey(key);
        }
    }

    private DeterministicKey encryptNonLeaf(KeyParameter aesKey, SimpleHDKeyChain chain,
                                            DeterministicKey parent, ImmutableList<ChildNumber> path) {
        DeterministicKey key = chain.hierarchy.get(path, true, false);
        key = key.encrypt(checkNotNull(simpleKeyChain.getKeyCrypter(), "Chain has null KeyCrypter"), aesKey, parent);
        hierarchy.putKey(key);
        simpleKeyChain.importKey(key);
        return key;
    }

    // Derives the account path keys and inserts them into the basic key chain. This is important to preserve their
    // order for serialization, amongst other things.
    private void initializeHierarchyUnencrypted(DeterministicKey baseKey) {
        rootKey = baseKey;
        addToBasicChain(rootKey);
        hierarchy = new DeterministicHierarchy(rootKey);
        externalKey = hierarchy.get(EXTERNAL_PATH, true, true);
        internalKey = hierarchy.get(INTERNAL_PATH, true, true);
        addToBasicChain(externalKey);
        addToBasicChain(internalKey);
    }

    public boolean isEncrypted() {
        lock.lock();
        try {
            return rootKey.isEncrypted();
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Get an unused key, without actually issuing it.
     * This is useful to show a receiving key in the interface
     */
    public DeterministicKey getCurrentUnusedKey(KeyPurpose purpose) {
        lock.lock();
        try {
            List<DeterministicKey> keys = null;
            switch (purpose) {
                case RECEIVE_FUNDS:
                case REFUND:
                    keys = getDeterministicKeys(1, externalKey, issuedExternalKeys + 1);
                    break;
                case CHANGE:
                    keys = getDeterministicKeys(1, internalKey, issuedInternalKeys + 1);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            return keys.get(0);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the last issued key
     */
    @Nullable
    public DeterministicKey getLastIssuedKey(KeyPurpose purpose) {
        lock.lock();
        try {
            List<DeterministicKey> keys;
            switch (purpose) {
                case RECEIVE_FUNDS:
                case REFUND:
                    if (issuedExternalKeys <= 0) return null;
                    keys = getDeterministicKeys(1, externalKey, issuedExternalKeys);
                    break;
                case CHANGE:
                    if (issuedInternalKeys <= 0) return null;
                    keys = getDeterministicKeys(1, internalKey, issuedInternalKeys);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            return keys.get(0);
        } finally {
            lock.unlock();
        }
    }

    /** Returns a freshly derived key that has not been returned by this method before. */
    @Override
    public DeterministicKey getKey(KeyPurpose purpose) {
        return getKeys(purpose, 1).get(0);
    }

    /** Returns freshly derived key/s that have not been returned by this method before. */
    @Override
    public List<DeterministicKey> getKeys(KeyPurpose purpose, int numberOfKeys) {
        checkArgument(numberOfKeys > 0, "Need at least 1 key");
        lock.lock();
        try {
            DeterministicKey parentKey;
            int index;
            switch (purpose) {
                // Map both REFUND and RECEIVE_KEYS to the same branch for now. Refunds are a feature of the BIP 70
                // payment protocol. Later we may wish to map it to a different branch (in a new wallet version?).
                // This would allow a watching wallet to only be able to see inbound payments, but not change
                // (i.e. spends) or refunds. Might be useful for auditing ...
                case RECEIVE_FUNDS:
                case REFUND:
                    issuedExternalKeys += numberOfKeys;
                    index = issuedExternalKeys;
                    parentKey = externalKey;
                    break;
                case CHANGE:
                    issuedInternalKeys += numberOfKeys;
                    index = issuedInternalKeys;
                    parentKey = internalKey;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            List<DeterministicKey> keys = getDeterministicKeys(numberOfKeys, parentKey, index);

            return keys;
        } finally {
            lock.unlock();
        }
    }

    private List<DeterministicKey> getDeterministicKeys(int numberOfKeys, DeterministicKey parentKey, int index) {
        lock.lock();
        try {
            // Optimization: potentially do a very quick key generation for just the number of keys we need if we
            // didn't already create them, ignoring the configured lookahead size. This ensures we'll be able to
            // retrieve the keys in the following loop, but if we're totally fresh and didn't get a chance to
            // calculate the lookahead keys yet, this will not block waiting to calculate 100+ EC point multiplies.
            // On slow/crappy Android phones looking ahead 100 keys can take ~5 seconds but the OS will kill us
            // if we block for just one second on the UI thread. Because UI threads may need an address in order
            // to render the screen, we need getKeys to be fast even if the wallet is totally brand new and lookahead
            // didn't happen yet.
            //
            // It's safe to do this because when a network thread tries to calculate a Bloom filter, we'll go ahead
            // and calculate the full lookahead zone there, so network requests will always use the right amount.
            List<DeterministicKey> lookahead = maybeLookAhead(parentKey, index, 0, 0);
            simpleKeyChain.importKeys(lookahead);
            List<DeterministicKey> keys = new ArrayList<DeterministicKey>(numberOfKeys);
            for (int i = 0; i < numberOfKeys; i++) {
                ImmutableList<ChildNumber> path = HDUtils.append(parentKey.getPath(),
                        new ChildNumber(index - numberOfKeys + i, false));
                keys.add(hierarchy.get(path, false, false));
            }
            return keys;
        } finally {
            lock.unlock();
        }
    }

    private void addToBasicChain(DeterministicKey key) {
        simpleKeyChain.importKeys(ImmutableList.of(key));
    }

    /**
     * Mark the DeterministicKey as used.
     * Also correct the issued{Internal|External}Keys counter, because all lower children seem to be requested already.
     * If the counter was updated, we also might trigger lookahead.
     */
    public DeterministicKey markKeyAsUsed(DeterministicKey k) {
        int numChildren = k.getChildNumber().i() + 1;

        if (k.getParent() == internalKey) {
            if (issuedInternalKeys < numChildren) {
                issuedInternalKeys = numChildren;
                maybeLookAhead();
            }
        } else if (k.getParent() == externalKey) {
            if (issuedExternalKeys < numChildren) {
                issuedExternalKeys = numChildren;
                maybeLookAhead();
            }
        }
        return k;
    }

    @Override
    public DeterministicKey findKeyFromPubHash(byte[] pubkeyHash) {
        lock.lock();
        try {
            return (DeterministicKey) simpleKeyChain.findKeyFromPubHash(pubkeyHash);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public DeterministicKey findKeyFromPubKey(byte[] pubkey) {
        lock.lock();
        try {
            return (DeterministicKey) simpleKeyChain.findKeyFromPubKey(pubkey);
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] bytes) {
        log.warn("Method findRedeemDataFromScriptHash not implemented");
        return null;
    }

    /**
     * Mark the DeterministicKeys as used, if they match the pubkeyHash
     * See {@link SimpleHDKeyChain#markKeyAsUsed(DeterministicKey)} for more info on this.
     */
    public boolean markPubHashAsUsed(byte[] pubkeyHash) {
        lock.lock();
        try {
            DeterministicKey k = (DeterministicKey) simpleKeyChain.findKeyFromPubHash(pubkeyHash);
            if (k != null)
                markKeyAsUsed(k);
            return k != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Mark the DeterministicKeys as used, if they match the pubkey
     * See {@link SimpleHDKeyChain#markKeyAsUsed(DeterministicKey)} for more info on this.
     */
    public boolean markPubKeyAsUsed(byte[] pubkey) {
        lock.lock();
        try {
            DeterministicKey k = (DeterministicKey) simpleKeyChain.findKeyFromPubKey(pubkey);
            if (k != null)
                markKeyAsUsed(k);
            return k != null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean hasKey(ECKey key) {
        lock.lock();
        try {
            return simpleKeyChain.hasKey(key);
        } finally {
            lock.unlock();
        }
    }

    /** Returns the deterministic key for the given absolute path in the hierarchy. */
    protected DeterministicKey getKeyByPath(ChildNumber... path) {
        return getKeyByPath(ImmutableList.<ChildNumber>copyOf(path));
    }

    /** Returns the deterministic key for the given absolute path in the hierarchy. */
    protected DeterministicKey getKeyByPath(ImmutableList<ChildNumber> path) {
        return hierarchy.get(path, false, false);
    }

    /**
     * <p>Use this when you would like to create a watching key chain that follows this one,
     * but can't spend money from it.</p>
     */
    public DeterministicKey getWatchingKey() {
        return rootKey.getPubOnly();
    }

    @Override
    public int numKeys() {
        lock.lock();
        try {
            maybeLookAhead();
            return simpleKeyChain.numKeys();
        } finally {
            lock.unlock();
        }
    }

    public DeterministicKey getRootKey() {
        return rootKey;
    }

    /**
     * Returns number of leaf keys used including both internal and external paths. This may be fewer than the number
     * that have been deserialized or held in memory, because of the lookahead zone.
     */
    public int numLeafKeysIssued() {
        lock.lock();
        try {
            return issuedExternalKeys + issuedInternalKeys;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long getEarliestKeyCreationTime() {
        return rootKey.getCreationTimeSeconds();
    }

    @Override
    public void addEventListener(KeyChainEventListener listener) {
        simpleKeyChain.addEventListener(listener);
    }

    @Override
    public void addEventListener(KeyChainEventListener listener, Executor executor) {
        simpleKeyChain.addEventListener(listener, executor);
    }

    @Override
    public boolean removeEventListener(KeyChainEventListener listener) {
        return simpleKeyChain.removeEventListener(listener);
    }

    /**
     * Return true if this keychain is following another keychain
     */
    public boolean isFollowing() {
        return false; // No support for now
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Serialization support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    List<Protos.Key> toProtobuf() {
        LinkedList<Protos.Key> entries = newLinkedList();
        List<Protos.Key.Builder> protos = toEditableProtobuf();
        for (Protos.Key.Builder proto : protos) {
            entries.add(proto.build());
        }
        return entries;
    }

    List<Protos.Key.Builder> toEditableProtobuf() {
        lock.lock();
        try {
            // Most of the serialization work is delegated to the basic key chain, which will serialize the bulk of the
            // data (handling encryption along the way), and letting us patch it up with the extra data we care about.
            LinkedList<Protos.Key.Builder> entries = newLinkedList();
            Map<ECKey, Protos.Key.Builder> keys = simpleKeyChain.toEditableProtobufs();
            for (Map.Entry<ECKey, Protos.Key.Builder> entry : keys.entrySet()) {
                DeterministicKey key = (DeterministicKey) entry.getKey();
                Protos.Key.Builder proto = entry.getValue();
                proto.setType(Protos.Key.Type.DETERMINISTIC_KEY);
                final Protos.DeterministicKey.Builder detKey = proto.getDeterministicKeyBuilder();
                detKey.setChainCode(ByteString.copyFrom(key.getChainCode()));
                for (ChildNumber num : key.getPath())
                    detKey.addPath(num.i());
                if (key.equals(externalKey)) {
                    detKey.setIssuedSubkeys(issuedExternalKeys);
                    detKey.setLookaheadSize(lookaheadSize);
                } else if (key.equals(internalKey)) {
                    detKey.setIssuedSubkeys(issuedInternalKeys);
                    detKey.setLookaheadSize(lookaheadSize);
                }
                // Flag the very first key of following keychain.
                if (entries.isEmpty() && isFollowing()) {
                    detKey.setIsFollowing(true);
                }
                entries.add(proto);
            }
            return entries;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the key chain found in the given list of keys. Used for unencrypted chains
     */
    public static SimpleHDKeyChain fromProtobuf(List<Protos.Key> keys) throws UnreadableWalletException {
        return fromProtobuf(keys, null);
    }

    /**
     * Returns the key chain found in the given list of keys.
     */
    public static SimpleHDKeyChain fromProtobuf(List<Protos.Key> keys, @Nullable KeyCrypter crypter) throws UnreadableWalletException {
        SimpleHDKeyChain chain = null;
        int lookaheadSize = -1;
        // If the root key is a child of another hierarchy, the depth will be > 0
        int rootTreeSize = 0;
        for (Protos.Key key : keys) {
            final Protos.Key.Type t = key.getType();
            if (t == Protos.Key.Type.DETERMINISTIC_KEY) {
                if (!key.hasDeterministicKey())
                    throw new UnreadableWalletException("Deterministic key missing extra data: " + key.toString());

                if (chain == null) {
                    DeterministicKey rootKey = KeyUtils.getDeterministicKey(key, null, crypter);
                    chain = new SimpleHDKeyChain(rootKey, crypter);
                    chain.lookaheadSize = LAZY_CALCULATE_LOOKAHEAD;
                    rootTreeSize = rootKey.getPath().size();
                }
                LinkedList<ChildNumber> path = newLinkedList(KeyUtils.getKeyProtoPath(key));
                // Find the parent key assuming this is not the root key, and not an account key for a watching chain.
                DeterministicKey parent = null;
                if (path.size() > rootTreeSize) {
                    ChildNumber index = path.removeLast();
                    parent = chain.hierarchy.get(path, false, false);
                    path.add(index);
                }
                DeterministicKey detkey = KeyUtils.getDeterministicKey(key, parent, crypter);
                if (log.isDebugEnabled()) {
                    log.debug("Deserializing: DETERMINISTIC_KEY: {}", detkey);
                }
                // If the non-encrypted case, the non-leaf keys (account, internal, external) have already been
                // rederived and inserted at this point and the two lines below are just a no-op. In the encrypted
                // case though, we can't rederive and we must reinsert, potentially building the heirarchy object
                // if need be.
                if (path.size() == rootTreeSize) {
                    // Master key.
                    chain.rootKey = detkey;
                    chain.hierarchy = new DeterministicHierarchy(detkey);
                } else if (path.size() == rootTreeSize + EXTERNAL_PATH.size()) {
                    if (EXTERNAL_PATH_NUM.equals(detkey.getChildNumber())) {
                        chain.externalKey = detkey;
                        chain.issuedExternalKeys = key.getDeterministicKey().getIssuedSubkeys();
                        lookaheadSize = Math.max(lookaheadSize, key.getDeterministicKey().getLookaheadSize());
                    } else if (INTERNAL_PATH_NUM.equals(detkey.getChildNumber())) {
                        chain.internalKey = detkey;
                        chain.issuedInternalKeys = key.getDeterministicKey().getIssuedSubkeys();
                    }
                }
                chain.hierarchy.putKey(detkey);
                chain.simpleKeyChain.importKey(detkey);
            }
        }
        if (chain == null) {
            throw new UnreadableWalletException("Could not create a key chain.");
        }
        checkState(lookaheadSize >= 0, "Negative lookahead size");
        chain.setLookaheadSize(lookaheadSize);
        chain.maybeLookAhead();
        return chain;
    }

    @Override
    public List<org.bitcoinj.wallet.Protos.Key> serializeToProtobuf() {
        throw new RuntimeException("Not implemented. Use toProtobuf() method instead.");
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Encryption support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SimpleHDKeyChain toEncrypted(CharSequence password) {
        checkNotNull(password, "Attempt to encrypt with a null password.");
        checkArgument(password.length() > 0, "Attempt to encrypt with an empty password.");
        checkState(!rootKey.isEncrypted(), "Attempt to encrypt a root key that is already encrypted.");
        checkState(!rootKey.isPubKeyOnly(), "Attempt to encrypt a watching chain.");
        KeyCrypter scrypt = new KeyCrypterScrypt();
        KeyParameter derivedKey = scrypt.deriveKey(password);
        return toEncrypted(scrypt, derivedKey);
    }

    @Override
    public SimpleHDKeyChain toEncrypted(KeyCrypter keyCrypter, KeyParameter aesKey) {
        return new SimpleHDKeyChain(keyCrypter, aesKey, this);
    }

    @Override
    public SimpleHDKeyChain toDecrypted(CharSequence password) {
        checkNotNull(password, "Attempt to decrypt with a null password.");
        checkArgument(password.length() > 0, "Attempt to decrypt with an empty password.");
        KeyCrypter crypter = getKeyCrypter();
        checkState(crypter != null, "Chain not encrypted");
        KeyParameter derivedKey = crypter.deriveKey(password);
        return toDecrypted(derivedKey);
    }

    @Override
    public SimpleHDKeyChain toDecrypted(KeyParameter aesKey) {
        checkState(getKeyCrypter() != null, "Key chain not encrypted");
        checkState(rootKey.isEncrypted(), "Root key not encrypted");
        DeterministicKey decKey = rootKey.decrypt(getKeyCrypter(), aesKey);
        SimpleHDKeyChain chain = new SimpleHDKeyChain(decKey);
        // Now double check that the keys match to catch the case where the key is wrong but padding didn't catch it.
        if (!chain.getWatchingKey().getPubKeyPoint().equals(getWatchingKey().getPubKeyPoint()))
            throw new KeyCrypterException("Provided AES key is wrong");
        chain.lookaheadSize = lookaheadSize;
        // Now copy the (pubkey only) leaf keys across to avoid rederiving them. The private key bytes are missing
        // anyway so there's nothing to decrypt.
        for (ECKey eckey : simpleKeyChain.getKeys()) {
            DeterministicKey key = (DeterministicKey) eckey;
            if (!isLeaf(key)) continue; // Not a leaf key.
            checkState(key.isEncrypted(), "Key is not encrypted");
            DeterministicKey parent = chain.hierarchy.get(checkNotNull(key.getParent(), "Key has null parent").getPath(), false, false);
            // Clone the key to the new decrypted hierarchy.
            key = new DeterministicKey(key.getPubOnly(), parent);
            chain.hierarchy.putKey(key);
            chain.simpleKeyChain.importKeys(key);
        }
        chain.issuedExternalKeys = issuedExternalKeys;
        chain.issuedInternalKeys = issuedInternalKeys;
        return chain;
    }

    private boolean isLeaf(DeterministicKey key) {
        return key.getPath().size() > internalKey.getPath().size();
    }

    @Override
    public boolean checkPassword(CharSequence password) {
        checkNotNull(password,"Password is null");
        checkState(getKeyCrypter() != null, "Key chain not encrypted");
        return checkAESKey(getKeyCrypter().deriveKey(password));
    }

    @Override
    public boolean checkAESKey(KeyParameter aesKey) {
        checkNotNull(aesKey, "Cannot check null KeyParameter");
        checkState(getKeyCrypter() != null, "Key chain not encrypted");
        try {
            return rootKey.decrypt(aesKey).getPubKeyPoint().equals(rootKey.getPubKeyPoint());
        } catch (KeyCrypterException e) {
            return false;
        }
    }

    @Nullable
    @Override
    public KeyCrypter getKeyCrypter() {
        return simpleKeyChain.getKeyCrypter();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Bloom filtering support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public int numBloomFilterEntries() {
        return numKeys() * 2;
    }

    @Override
    public BloomFilter getFilter(int size, double falsePositiveRate, long tweak) {
        lock.lock();
        try {
            checkArgument(size >= numBloomFilterEntries(), "Bloom filter too small");
            maybeLookAhead();
            return simpleKeyChain.getFilter(size, falsePositiveRate, tweak);
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>The number of public keys we should pre-generate on each path before they are requested by the app. This is
     * required so that when scanning through the chain given only a seed, we can give enough keys to the remote node
     * via the Bloom filter such that we see transactions that are "from the future", for example transactions created
     * by a different app that's sharing the same seed, or transactions we made before but we're replaying the chain
     * given just the seed. The default is 100.</p>
     */
    public int getLookaheadSize() {
        lock.lock();
        try {
            return lookaheadSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets a new lookahead size. See {@link #getLookaheadSize()} for details on what this is. Setting a new size
     * that's larger than the current size will return immediately and the new size will only take effect next time
     * a fresh filter is requested (e.g. due to a new peer being connected). So you should set this before starting
     * to sync the chain, if you want to modify it. If you haven't modified the lookahead threshold manually then
     * it will be automatically set to be a third of the new size.
     */
    public void setLookaheadSize(int lookaheadSize) {
        lock.lock();
        try {
            boolean readjustThreshold = this.lookaheadThreshold == calcDefaultLookaheadThreshold();
            this.lookaheadSize = lookaheadSize;
            if (readjustThreshold)
                this.lookaheadThreshold = calcDefaultLookaheadThreshold();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the threshold for the key pre-generation.
     * If a key is used in a transaction, the keychain would pre-generate a new key, for every issued key,
     * even if it is only one. If the blockchain is replayed, every key would trigger a regeneration
     * of the bloom filter sent to the peers as a consequence.
     * To prevent this, new keys are only generated, if more than the threshold value are needed.
     */
    public void setLookaheadThreshold(int num) {
        lock.lock();
        try {
            if (num >= lookaheadSize)
                throw new IllegalArgumentException("Threshold larger or equal to the lookaheadSize");
            this.lookaheadThreshold = num;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the threshold for the key pre-generation.
     * See {@link #setLookaheadThreshold(int)} for details on what this is.
     */
    public int getLookaheadThreshold() {
        lock.lock();
        try {
            if (lookaheadThreshold >= lookaheadSize)
                return 0;
            return lookaheadThreshold;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Pre-generate enough keys to reach the lookahead size. You can call this if you need to explicitly invoke
     * the lookahead procedure, but it's normally unnecessary as it will be done automatically when needed.
     */
    public void maybeLookAhead() {
        lock.lock();
        try {
            List<DeterministicKey> keys = maybeLookAhead(externalKey, issuedExternalKeys);
            keys.addAll(maybeLookAhead(internalKey, issuedInternalKeys));
            // Batch add all keys at once so there's only one event listener invocation, as this will be listened to
            // by the wallet and used to rebuild/broadcast the Bloom filter. That's expensive so we don't want to do
            // it more often than necessary.
            simpleKeyChain.importKeys(keys);
        } finally {
            lock.unlock();
        }
    }

    private List<DeterministicKey> maybeLookAhead(DeterministicKey parent, int issued) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        return maybeLookAhead(parent, issued, getLookaheadSize(), getLookaheadThreshold());
    }

    /**
     * Pre-generate enough keys to reach the lookahead size, but only if there are more than the lookaheadThreshold to
     * be generated, so that the Bloom filter does not have to be regenerated that often.
     *
     * The returned mutable list of keys must be inserted into the basic key chain.
     */
    private List<DeterministicKey> maybeLookAhead(DeterministicKey parent, int issued, int lookaheadSize, int lookaheadThreshold) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        final int numChildren = hierarchy.getNumChildren(parent.getPath());
        final int needed = issued + lookaheadSize + lookaheadThreshold - numChildren;

        if (needed <= lookaheadThreshold)
            return new ArrayList<DeterministicKey>();

        log.info("{} keys needed for {} = {} issued + {} lookahead size + {} lookahead threshold - {} num children",
                needed, parent.getPathAsString(), issued, lookaheadSize, lookaheadThreshold, numChildren);

        List<DeterministicKey> result  = new ArrayList<DeterministicKey>(needed);
        long now = System.currentTimeMillis();
        int nextChild = numChildren;
        for (int i = 0; i < needed; i++) {
            DeterministicKey key = HDKeyDerivation.deriveThisOrNextChildKey(parent, nextChild);
            key = key.getPubOnly();
            hierarchy.putKey(key);
            result.add(key);
            nextChild = key.getChildNumber().num() + 1;
        }
        log.info("Took {} msec", System.currentTimeMillis() - now);
        return result;
    }

    /**
     * Returns keys used on external path. This may be fewer than the number that have been deserialized
     * or held in memory, because of the lookahead zone.
     */
    public ArrayList<DeterministicKey> getIssuedExternalKeys() {
        lock.lock();
        try {
            maybeLookAhead();
            int treeSize = externalKey.getPath().size();
            ArrayList<DeterministicKey> issuedKeys = new ArrayList<DeterministicKey>();
            for (ECKey key : simpleKeyChain.getKeys()) {
                DeterministicKey detkey = (DeterministicKey) key;
                DeterministicKey parent = detkey.getParent();
                if (parent == null) continue;
                if (detkey.getPath().size() <= treeSize) continue;
                if (parent.equals(internalKey)) continue;
                if (parent.equals(externalKey) && detkey.getChildNumber().num() >= issuedExternalKeys) continue;
                issuedKeys.add(detkey);
            }
            return issuedKeys;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns number of keys used on external path. This may be fewer than the number that have been deserialized
     * or held in memory, because of the lookahead zone.
     */
    public int getNumIssuedExternalKeys() {
        lock.lock();
        try {
            return issuedExternalKeys;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns number of keys used on internal path. This may be fewer than the number that have been deserialized
     * or held in memory, because of the lookahead zone.
     */
    public int getNumIssuedInternalKeys() {
        lock.lock();
        try {
            return issuedInternalKeys;
        } finally {
            lock.unlock();
        }
    }

    // For internal usage only
    /* package */ List<ECKey> getKeys(boolean includeLookahead) {
        maybeLookAhead();
        List<ECKey> keys = simpleKeyChain.getKeys();
        if (!includeLookahead) {
            int treeSize = internalKey.getPath().size();
            List<ECKey> issuedKeys = new LinkedList<ECKey>();
            for (ECKey key : keys) {
                DeterministicKey detkey = (DeterministicKey) key;
                DeterministicKey parent = detkey.getParent();
                if (parent == null) continue;
                if (detkey.getPath().size() <= treeSize) continue;
                if (parent.equals(internalKey) && detkey.getChildNumber().num() > issuedInternalKeys) continue;
                if (parent.equals(externalKey) && detkey.getChildNumber().num() > issuedExternalKeys) continue;
                issuedKeys.add(detkey);
            }
            return issuedKeys;
        }
        return keys;
    }


    /**
     * Returns leaf keys issued by this chain (including lookahead zone but no lookahead threshold)
     */
    public List<DeterministicKey> getActiveKeys() {
        ImmutableList.Builder<DeterministicKey> keys = ImmutableList.builder();
        for (ECKey key : getKeys(true)) {
            DeterministicKey dKey = (DeterministicKey) key;
            if (isLeaf(dKey)) {
                if (dKey.getParent().equals(internalKey) && dKey.getChildNumber().num() >= issuedInternalKeys + lookaheadSize) continue;
                if (dKey.getParent().equals(externalKey) && dKey.getChildNumber().num() >= issuedExternalKeys + lookaheadSize) continue;

                keys.add(dKey);
            }
        }
        return keys.build();
    }

    public boolean isExternal(DeterministicKey key) {
        return key.getParent() != null && key.getParent().equals(externalKey);
    }

    public int getAccountIndex() {
        return rootKey.getChildNumber().num();
    }
}

