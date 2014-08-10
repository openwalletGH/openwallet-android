package com.coinomi.core;


import com.coinomi.core.keychains.EncryptableKeyChain;
import com.coinomi.core.protos.Protos;
import com.google.bitcoin.core.BloomFilter;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.crypto.ChildNumber;
import com.google.bitcoin.crypto.DeterministicHierarchy;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.HDKeyDerivation;
import com.google.bitcoin.crypto.HDUtils;
import com.google.bitcoin.crypto.KeyCrypter;
import com.google.bitcoin.crypto.KeyCrypterException;
import com.google.bitcoin.crypto.KeyCrypterScrypt;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.wallet.BasicKeyChain;
import com.google.bitcoin.wallet.KeyChainEventListener;
import com.google.common.collect.ImmutableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Giannis Dzegoutanis
 * @author 2013 The bitcoinj developers.
 */


public class HDKeyChain implements EncryptableKeyChain {
    private static final Logger log = LoggerFactory.getLogger(HDKeyChain.class);

    private final ReentrantLock lock = Threading.lock("KeyChain");

    private DeterministicHierarchy hierarchy;
    private DeterministicKey rootKey;

    // Paths through the key tree. External keys are ones that are communicated to other parties. Internal keys are
    // keys created for change addresses, coinbases, mixing, etc - anything that isn't communicated. The distinction
    // is somewhat arbitrary but can be useful for audits. The first number is the "account number" but we don't use
    // that feature yet. In future we might hand out different accounts for cases where we wish to hand payers
    // a payment request that can generate lots of addresses independently.
    public static final ImmutableList<ChildNumber> ACCOUNT_ZERO_PATH = ImmutableList.of(ChildNumber.ZERO_HARDENED);
    public static final ImmutableList<ChildNumber> EXTERNAL_PATH = ImmutableList.of(ChildNumber.ZERO_HARDENED, ChildNumber.ZERO);
    public static final ImmutableList<ChildNumber> INTERNAL_PATH = ImmutableList.of(ChildNumber.ZERO_HARDENED, ChildNumber.ONE);

    // We try to ensure we have at least this many keys ready and waiting to be handed out via getKey().
    // See docs for getLookaheadSize() for more info on what this is for. The -1 value means it hasn't been calculated
    // yet. For new chains it's set to whatever the default is, unless overridden by setLookaheadSize. For deserialized
    // chains, it will be calculated on demand from the number of loaded keys.
    private static final int LAZY_CALCULATE_LOOKAHEAD = -1;
    private int lookaheadSize = Constants.LOOKAHEAD;
    // The lookahead threshold causes us to batch up creation of new keys to minimize the frequency of Bloom filter
    // regenerations, which are expensive and will (in future) trigger chain download stalls/retries. One third
    // is an efficiency tradeoff.
    private int lookaheadThreshold = lookaheadSize / 3;

    // The parent keys for external keys (handed out to other people) and internal keys (used for change addresses).
    private DeterministicKey externalKey, internalKey;
    // How many keys on each path have actually been used. This may be fewer than the number that have been deserialized
    // or held in memory, because of the lookahead zone.
    private int issuedExternalKeys, issuedInternalKeys;

    // We simplify by wrapping a basic key chain and that way we get some functionality like key lookup and event
    // listeners "for free". All keys in the key tree appear here, even if they aren't meant to be used for receiving
    // money.
    private final BasicKeyChain basicKeyChain;

    /**
     * Creates a deterministic key chain that watches the given (public only) root key. You can use this to calculate
     * balances and generally follow along, but spending is not possible with such a chain. Currently you can't use
     * this method to watch an arbitrary fragment of some other tree, this limitation may be removed in future.
     */
    public HDKeyChain(DeterministicKey rootkey) {
        basicKeyChain = new BasicKeyChain();
        initializeHierarchyUnencrypted(rootkey);
    }

    // For use in encryption.
    private HDKeyChain(KeyCrypter crypter, KeyParameter aesKey, HDKeyChain chain) {
        checkArgument(!chain.rootKey.isEncrypted(), "Chain already encrypted");

        this.issuedExternalKeys = chain.issuedExternalKeys;
        this.issuedInternalKeys = chain.issuedInternalKeys;

        this.lookaheadSize = chain.lookaheadSize;
        this.lookaheadThreshold = chain.lookaheadThreshold;

        basicKeyChain = new BasicKeyChain(crypter);
        // The first number is the "account number" but we don't use that feature.
        rootKey = chain.rootKey.encrypt(crypter, aesKey, null);
        hierarchy = new DeterministicHierarchy(rootKey);
        basicKeyChain.importKey(rootKey);

        DeterministicKey account = encryptNonLeaf(aesKey, chain, rootKey, ACCOUNT_ZERO_PATH);
        externalKey = encryptNonLeaf(aesKey, chain, account, EXTERNAL_PATH);
        internalKey = encryptNonLeaf(aesKey, chain, account, INTERNAL_PATH);

        // Now copy the (pubkey only) leaf keys across to avoid rederiving them. The private key bytes are missing
        // anyway so there's nothing to encrypt.
        for (ECKey eckey : chain.basicKeyChain.getKeys()) {
            DeterministicKey key = (DeterministicKey) eckey;
            if (key.getPath().size() != 3) continue; // Not a leaf key.
            DeterministicKey parent = hierarchy.get(checkNotNull(key.getParent()).getPath(), true, false);
            // Clone the key to the new encrypted hierarchy.
            key = new DeterministicKey(key.getPubOnly(), parent);
            hierarchy.putKey(key);
            basicKeyChain.importKey(key);
        }
    }

    private DeterministicKey encryptNonLeaf(KeyParameter aesKey, HDKeyChain chain,
                                            DeterministicKey parent, ImmutableList<ChildNumber> path) {
        DeterministicKey key = chain.hierarchy.get(path, true, false);
        key = key.encrypt(checkNotNull(basicKeyChain.getKeyCrypter()), aesKey, parent);
        hierarchy.putKey(key);
        basicKeyChain.importKey(key);
        return key;
    }

    // Derives the account path keys and inserts them into the basic key chain. This is important to preserve their
    // order for serialization, amongst other things.
    private void initializeHierarchyUnencrypted(DeterministicKey baseKey) {
        rootKey = baseKey;
        addToBasicChain(rootKey);
        hierarchy = new DeterministicHierarchy(rootKey);
        addToBasicChain(hierarchy.get(ACCOUNT_ZERO_PATH, true, true));
        externalKey = hierarchy.deriveChild(ACCOUNT_ZERO_PATH, true, false, ChildNumber.ZERO);
        internalKey = hierarchy.deriveChild(ACCOUNT_ZERO_PATH, true, false, ChildNumber.ONE);
        addToBasicChain(externalKey);
        addToBasicChain(internalKey);
    }

    /** Returns a freshly derived key that has not been returned by this method before. */
    @Override
    public DeterministicKey getKey(KeyPurpose purpose) {
        return getKeys(purpose,1).get(0);
    }

    /** Returns freshly derived key/s that have not been returned by this method before. */
    @Override
    public List<DeterministicKey> getKeys(KeyPurpose purpose,int numberOfKeys) {
        checkArgument(numberOfKeys > 0);
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
            List<DeterministicKey> lookahead = maybeLookAhead(parentKey, index);
            basicKeyChain.importKeys(lookahead);
            List<DeterministicKey> keys = new ArrayList<DeterministicKey>(numberOfKeys);

            for (int i = 1; i <= numberOfKeys; i++) {
                keys.add(hierarchy.get(HDUtils.append(parentKey.getPath(), new ChildNumber((index-numberOfKeys+i) - 1, false)), false, false));
            }
            return keys;
        } finally {
            lock.unlock();
        }
    }

    private void addToBasicChain(DeterministicKey key) {
        basicKeyChain.importKeys(ImmutableList.of(key));
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
            return (DeterministicKey) basicKeyChain.findKeyFromPubHash(pubkeyHash);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public DeterministicKey findKeyFromPubKey(byte[] pubkey) {
        lock.lock();
        try {
            return (DeterministicKey) basicKeyChain.findKeyFromPubKey(pubkey);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Mark the DeterministicKeys as used, if they match the pubkeyHash
     * See {@link HDKeyChain#markKeyAsUsed(DeterministicKey)} for more info on this.
     */
    public boolean markPubHashAsUsed(byte[] pubkeyHash) {
        lock.lock();
        try {
            DeterministicKey k = (DeterministicKey) basicKeyChain.findKeyFromPubHash(pubkeyHash);
            if (k != null)
                markKeyAsUsed(k);
            return k != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Mark the DeterministicKeys as used, if they match the pubkey
     * See {@link HDKeyChain#markKeyAsUsed(DeterministicKey)} for more info on this.
     */
    public boolean markPubKeyAsUsed(byte[] pubkey) {
        lock.lock();
        try {
            DeterministicKey k = (DeterministicKey) basicKeyChain.findKeyFromPubKey(pubkey);
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
            return basicKeyChain.hasKey(key);
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
     * <p>An alias for <code>getKeyByPath(KeyChain.ACCOUNT_ZERO_PATH).getPubOnly()</code>.
     * Use this when you would like to create a watching key chain that follows this one, but can't spend money from it.</p>
     */
    public DeterministicKey getWatchingKey() {
        return getKeyByPath(ACCOUNT_ZERO_PATH).getPubOnly();
    }

    @Override
    public int numKeys() {
        // We need to return here the total number of keys including the lookahead zone, not the number of keys we
        // have issued via getKey/freshReceiveKey.
        return basicKeyChain.numKeys();
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
        basicKeyChain.addEventListener(listener);
    }

    @Override
    public void addEventListener(KeyChainEventListener listener, Executor executor) {
        basicKeyChain.addEventListener(listener, executor);
    }

    @Override
    public boolean removeEventListener(KeyChainEventListener listener) {
        return basicKeyChain.removeEventListener(listener);
    }


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Serialization support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public List<Protos.Key> serializeToProtobuf() {
        throw new RuntimeException("not implemented");
//        lock.lock();
//        try {
//            // Most of the serialization work is delegated to the basic key chain, which will serialize the bulk of the
//            // data (handling encryption along the way), and letting us patch it up with the extra data we care about.
//            LinkedList<Protos.Key> entries = newLinkedList();
//            Map<ECKey, Protos.Key.Builder> keys = basicKeyChain.serializeToEditableProtobufs();
//            for (Map.Entry<ECKey, Protos.Key.Builder> entry : keys.entrySet()) {
//                DeterministicKey key = (DeterministicKey) entry.getKey();
//                Protos.Key.Builder proto = entry.getValue();
//                proto.setType(Protos.Key.Type.DETERMINISTIC_KEY);
//                final Protos.DeterministicKey.Builder detKey = proto.getDeterministicKeyBuilder();
//                detKey.setChainCode(ByteString.copyFrom(key.getChainCode()));
//                for (ChildNumber num : key.getPath())
//                    detKey.addPath(num.i());
//                if (key.equals(externalKey)) {
//                    detKey.setIssuedSubkeys(issuedExternalKeys);
//                    detKey.setLookaheadSize(lookaheadSize);
//                } else if (key.equals(internalKey)) {
//                    detKey.setIssuedSubkeys(issuedInternalKeys);
//                    detKey.setLookaheadSize(lookaheadSize);
//                }
//                // Flag the very first key of following keychain.
//                if (entries.isEmpty() && isFollowing()) {
//                    detKey.setIsFollowing(true);
//                }
//                if (key.getParent() != null) {
//                    // HD keys inherit the timestamp of their parent if they have one, so no need to serialize it.
//                    proto.clearCreationTimestamp();
//                }
//                entries.add(proto.build());
//            }
//            return entries;
//        } finally {
//            lock.unlock();
//        }
    }

    /**
     * Returns all the key chains found in the given list of keys. Typically there will only be one, but in the case of
     * key rotation it can happen that there are multiple chains found.
     */
    public static List<HDKeyChain> fromProtobuf(List<Protos.Key> keys, @Nullable KeyCrypter crypter) throws UnreadableWalletException {
        throw new RuntimeException("not implemented");
//        List<KeyChain> chains = newLinkedList();
//        DeterministicSeed seed = null;
//        KeyChain chain = null;
//        int lookaheadSize = -1;
//        for (Protos.Key key : keys) {
//            final Protos.Key.Type t = key.getType();
//            if (t == Protos.Key.Type.DETERMINISTIC_ROOT_SEED) {
//                if (chain != null) {
//                    checkState(lookaheadSize >= 0);
//                    chain.setLookaheadSize(lookaheadSize);
//                    chain.maybeLookAhead();
//                    chains.add(chain);
//                    chain = null;
//                }
//                long timestamp = key.getCreationTimestamp() / 1000;
//                if (key.hasSecretBytes()) {
//                    seed = new DeterministicSeed(key.getSecretBytes().toByteArray(), timestamp);
//                } else if (key.hasEncryptedData()) {
//                    EncryptedData data = new EncryptedData(key.getEncryptedData().getInitialisationVector().toByteArray(),
//                            key.getEncryptedData().getEncryptedPrivateKey().toByteArray());
//                    seed = new DeterministicSeed(data, timestamp);
//                } else {
//                    throw new UnreadableWalletException("Malformed key proto: " + key.toString());
//                }
//                if (log.isDebugEnabled())
//                    log.debug("Deserializing: DETERMINISTIC_ROOT_SEED: {}", seed);
//            } else if (t == Protos.Key.Type.DETERMINISTIC_KEY) {
//                if (!key.hasDeterministicKey())
//                    throw new UnreadableWalletException("Deterministic key missing extra data: " + key.toString());
//                byte[] chainCode = key.getDeterministicKey().getChainCode().toByteArray();
//                // Deserialize the path through the tree.
//                LinkedList<ChildNumber> path = newLinkedList();
//                for (int i : key.getDeterministicKey().getPathList())
//                    path.add(new ChildNumber(i));
//                // Deserialize the public key and path.
//                ECPoint pubkey = ECKey.CURVE.getCurve().decodePoint(key.getPublicKey().toByteArray());
//                final ImmutableList<ChildNumber> immutablePath = ImmutableList.copyOf(path);
//                // Possibly create the chain, if we didn't already do so yet.
//                boolean isWatchingAccountKey = false;
//                boolean isFollowingKey = false;
//                // save previous chain if any if the key is marked as following. Current key and the next ones are to be
//                // placed in new following key chain
//                if (key.getDeterministicKey().getIsFollowing()) {
//                    if (chain != null) {
//                        checkState(lookaheadSize >= 0);
//                        chain.setLookaheadSize(lookaheadSize);
//                        chain.maybeLookAhead();
//                        chains.add(chain);
//                        chain = null;
//                        seed = null;
//                    }
//                    isFollowingKey = true;
//                }
//                if (chain == null) {
//                    if (seed == null) {
//                        DeterministicKey accountKey = new DeterministicKey(immutablePath, chainCode, pubkey, null, null);
//                        if (!accountKey.getPath().equals(ACCOUNT_ZERO_PATH))
//                            throw new UnreadableWalletException("Expecting account key but found key with path: " +
//                                    HDUtils.formatPath(accountKey.getPath()));
//                        chain = new KeyChain(accountKey, isFollowingKey);
//                        isWatchingAccountKey = true;
//                    } else {
//                        chain = new KeyChain(seed, crypter);
//                        chain.lookaheadSize = LAZY_CALCULATE_LOOKAHEAD;
//                        // If the seed is encrypted, then the chain is incomplete at this point. However, we will load
//                        // it up below as we parse in the keys. We just need to check at the end that we've loaded
//                        // everything afterwards.
//                    }
//                }
//                // Find the parent key assuming this is not the root key, and not an account key for a watching chain.
//                DeterministicKey parent = null;
//                if (!path.isEmpty() && !isWatchingAccountKey) {
//                    ChildNumber index = path.removeLast();
//                    parent = chain.hierarchy.get(path, false, false);
//                    path.add(index);
//                }
//                DeterministicKey detkey;
//                if (key.hasSecretBytes()) {
//                    // Not encrypted: private key is available.
//                    final BigInteger priv = new BigInteger(1, key.getSecretBytes().toByteArray());
//                    detkey = new DeterministicKey(immutablePath, chainCode, pubkey, priv, parent);
//                } else {
//                    if (key.hasEncryptedData()) {
//                        Protos.EncryptedData proto = key.getEncryptedData();
//                        EncryptedData data = new EncryptedData(proto.getInitialisationVector().toByteArray(),
//                                proto.getEncryptedPrivateKey().toByteArray());
//                        checkNotNull(crypter, "Encountered an encrypted key but no key crypter provided");
//                        detkey = new DeterministicKey(immutablePath, chainCode, crypter, pubkey, data, parent);
//                    } else {
//                        // No secret key bytes and key is not encrypted: either a watching key or private key bytes
//                        // will be rederived on the fly from the parent.
//                        detkey = new DeterministicKey(immutablePath, chainCode, pubkey, null, parent);
//                    }
//                }
//                if (key.hasCreationTimestamp())
//                    detkey.setCreationTimeSeconds(key.getCreationTimestamp() / 1000);
//                if (log.isDebugEnabled())
//                    log.debug("Deserializing: DETERMINISTIC_KEY: {}", detkey);
//                if (!isWatchingAccountKey) {
//                    // If the non-encrypted case, the non-leaf keys (account, internal, external) have already been
//                    // rederived and inserted at this point and the two lines below are just a no-op. In the encrypted
//                    // case though, we can't rederive and we must reinsert, potentially building the heirarchy object
//                    // if need be.
//                    if (path.size() == 0) {
//                        // Master key.
//                        chain.rootKey = detkey;
//                        chain.hierarchy = new DeterministicHierarchy(detkey);
//                    } else if (path.size() == 2) {
//                        if (detkey.getChildNumber().num() == 0) {
//                            chain.externalKey = detkey;
//                            chain.issuedExternalKeys = key.getDeterministicKey().getIssuedSubkeys();
//                            lookaheadSize = Math.max(lookaheadSize, key.getDeterministicKey().getLookaheadSize());
//                        } else if (detkey.getChildNumber().num() == 1) {
//                            chain.internalKey = detkey;
//                            chain.issuedInternalKeys = key.getDeterministicKey().getIssuedSubkeys();
//                        }
//                    }
//                }
//                chain.hierarchy.putKey(detkey);
//                chain.basicKeyChain.importKey(detkey);
//            }
//        }
//        if (chain != null) {
//            checkState(lookaheadSize >= 0);
//            chain.setLookaheadSize(lookaheadSize);
//            chain.maybeLookAhead();
//            chains.add(chain);
//        }
//        return chains;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Encryption support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public HDKeyChain toEncrypted(CharSequence password) {
        checkNotNull(password);
        checkArgument(password.length() > 0);
        checkState(rootKey.isPubKeyOnly() != false, "Attempt to encrypt a watching chain.");
        checkState(!rootKey.isEncrypted());
        KeyCrypter scrypt = new KeyCrypterScrypt();
        KeyParameter derivedKey = scrypt.deriveKey(password);
        return toEncrypted(scrypt, derivedKey);
    }

    @Override
    public HDKeyChain toEncrypted(KeyCrypter keyCrypter, KeyParameter aesKey) {
        return new HDKeyChain(keyCrypter, aesKey, this);
    }

    @Override
    public HDKeyChain toDecrypted(CharSequence password) {
        checkNotNull(password);
        checkArgument(password.length() > 0);
        KeyCrypter crypter = getKeyCrypter();
        checkState(crypter != null, "Chain not encrypted");
        KeyParameter derivedKey = crypter.deriveKey(password);
        return toDecrypted(derivedKey);
    }

    @Override
    public HDKeyChain toDecrypted(KeyParameter aesKey) {
        checkState(getKeyCrypter() != null, "Key chain not encrypted");
        checkState(rootKey.isEncrypted());
        DeterministicKey decKey = rootKey.decrypt(getKeyCrypter(), aesKey);
        HDKeyChain chain = new HDKeyChain(decKey);
        // Now double check that the keys match to catch the case where the key is wrong but padding didn't catch it.
        if (!chain.getWatchingKey().getPubKeyPoint().equals(getWatchingKey().getPubKeyPoint()))
            throw new KeyCrypterException("Provided AES key is wrong");
        chain.lookaheadSize = lookaheadSize;
        // Now copy the (pubkey only) leaf keys across to avoid rederiving them. The private key bytes are missing
        // anyway so there's nothing to decrypt.
        for (ECKey eckey : basicKeyChain.getKeys()) {
            DeterministicKey key = (DeterministicKey) eckey;
            if (key.getPath().size() != 3) continue; // Not a leaf key.
            checkState(key.isEncrypted());
            DeterministicKey parent = chain.hierarchy.get(checkNotNull(key.getParent()).getPath(), true, false);
            // Clone the key to the new decrypted hierarchy.
            key = new DeterministicKey(key.getPubOnly(), parent);
            chain.hierarchy.putKey(key);
            chain.basicKeyChain.importKeys(key);
        }
        chain.issuedExternalKeys = issuedExternalKeys;
        chain.issuedInternalKeys = issuedInternalKeys;
        return chain;
    }

    @Override
    public boolean checkPassword(CharSequence password) {
        checkNotNull(password);
        checkState(getKeyCrypter() != null, "Key chain not encrypted");
        return checkAESKey(getKeyCrypter().deriveKey(password));
    }

    @Override
    public boolean checkAESKey(KeyParameter aesKey) {
        checkNotNull(aesKey);
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
        return basicKeyChain.getKeyCrypter();
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
        checkArgument(size >= numBloomFilterEntries());
        return basicKeyChain.getFilter(size, falsePositiveRate, tweak);
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
     * to sync the chain, if you want to modify it.
     */
    public void setLookaheadSize(int lookaheadSize) {
        lock.lock();
        try {
            this.lookaheadSize = lookaheadSize;
            if (this.lookaheadThreshold >= lookaheadSize)
                setLookaheadThreshold(lookaheadSize - 1);
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

    // Pre-generate enough keys to reach the lookahead size.
    private void maybeLookAhead() {
        lock.lock();
        try {
            List<DeterministicKey> keys = maybeLookAhead(externalKey, issuedExternalKeys);
            keys.addAll(maybeLookAhead(internalKey, issuedInternalKeys));
            // Batch add all keys at once so there's only one event listener invocation, as this will be listened to
            // by the wallet and used to rebuild/broadcast the Bloom filter. That's expensive so we don't want to do
            // it more often than necessary.
            basicKeyChain.importKeys(keys);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Pre-generate enough keys to reach the lookahead size, but only if there are more than the lookaheadThreshold to
     * be generated, so that the Bloom filter does not have to be regenerated that often.
     *
     * The return mutable list of keys must be inserted into the basic key chain.
     */
    private List<DeterministicKey> maybeLookAhead(DeterministicKey parent, int issued) {
        checkState(lock.isHeldByCurrentThread());
        final int numChildren = hierarchy.getNumChildren(parent.getPath());
        final int needed = issued + getLookaheadSize() - numChildren;

        log.info("maybeLookAhead(): {} needed = lookaheadSize({}) - (numChildren({}) - issued({}) = {} < lookaheadThreshold({}))",
                parent.getPathAsString(), getLookaheadSize(), numChildren,
                issued, needed, getLookaheadThreshold());

        /* Even if needed is negative, we have more than enough */
        if (needed <= getLookaheadThreshold())
            return new ArrayList<DeterministicKey>();

        List<DeterministicKey> result  = new ArrayList<DeterministicKey>(needed);
        long now = System.currentTimeMillis();
        log.info("maybeLookAhead(): Pre-generating {} keys for {}", needed, parent.getPathAsString());
        int nextChild = numChildren;
        for (int i = 0; i < needed; i++) {
            DeterministicKey key = HDKeyDerivation.deriveThisOrNextChildKey(parent, nextChild);
            key = key.getPubOnly();
            hierarchy.putKey(key);
            result.add(key);
            nextChild = key.getChildNumber().num() + 1;
        }
        log.info("maybeLookAhead(): Took {} msec", System.currentTimeMillis() - now);
        return result;
    }

    /**
     * Returns number of keys used on external path. This may be fewer than the number that have been deserialized
     * or held in memory, because of the lookahead zone.
     */
    public int getIssuedExternalKeys() {
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
    public int getIssuedInternalKeys() {
        lock.lock();
        try {
            return issuedInternalKeys;
        } finally {
            lock.unlock();
        }
    }

    // For internal usage only (for printing keys in KeyChainGroup).
    /* package */ List<ECKey> getKeys() {
        return basicKeyChain.getKeys();
    }


    /**
     * Returns leaf keys issued by this chain (not including lookahead zone)
     */
    public List<DeterministicKey> getLeafKeys() {
        ImmutableList.Builder<DeterministicKey> keys = ImmutableList.builder();
        for (ECKey key : getKeys()) {
            DeterministicKey dKey = (DeterministicKey) key;
            if (dKey.getPath().size() > 2) {
                keys.add(dKey);
            }
        }
        return keys.build();
    }
}

