package com.coinomi.core.wallet;

import com.coinomi.core.CoreUtils;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.families.BitFamily;
import com.coinomi.core.coins.families.NxtFamily;
import com.coinomi.core.exceptions.UnsupportedCoinTypeException;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.wallet.families.nxt.NxtFamilyWallet;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import static com.coinomi.core.CoreUtils.bytesToMnemonic;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
final public class Wallet {
    private static final Logger log = LoggerFactory.getLogger(Wallet.class);
    public static int ENTROPY_SIZE_DEBUG = -1;

    private final ReentrantLock lock = Threading.lock("KeyChain");

    @GuardedBy("lock") private final LinkedHashMap<CoinType, ArrayList<WalletAccount>> accountsByType;
    @GuardedBy("lock") private final LinkedHashMap<String, WalletAccount> accounts;

    @Nullable private DeterministicSeed seed;
    private DeterministicKey masterKey;

    protected volatile WalletFiles vFileManager;

    // FIXME, make multi account capable
    private final static int ACCOUNT_ZERO = 0;

    private int version = 2;

    public Wallet(String mnemonic) throws MnemonicException {
        this(CoreUtils.parseMnemonic(mnemonic), null);
    }

    public Wallet(List<String> mnemonic) throws MnemonicException {
        this(mnemonic, null);
    }

    public Wallet(List<String> mnemonic, @Nullable String password) throws MnemonicException {
        MnemonicCode.INSTANCE.check(mnemonic);
        password = password == null ? "" : password;

        seed = new DeterministicSeed(mnemonic, null, password, 0);
        masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
        accountsByType = new LinkedHashMap<CoinType, ArrayList<WalletAccount>>();
        accounts = new LinkedHashMap<String, WalletAccount>();
    }

    public Wallet(DeterministicKey masterKey, @Nullable DeterministicSeed seed) {
        this.seed = seed;
        this.masterKey = masterKey;
        accountsByType = new LinkedHashMap<CoinType, ArrayList<WalletAccount>>();
        accounts = new LinkedHashMap<String, WalletAccount>();
    }

    public static List<String> generateMnemonic(int entropyBitsSize) {
        byte[] entropy;
        if (ENTROPY_SIZE_DEBUG > 0) {
            entropy = new byte[ENTROPY_SIZE_DEBUG];
        } else {
            entropy = new byte[entropyBitsSize / 8];
        }

        SecureRandom sr = new SecureRandom();
        sr.nextBytes(entropy);

        return bytesToMnemonic(entropy);
    }

    public static String generateMnemonicString(int entropyBitsSize) {
        List<String> mnemonicWords = Wallet.generateMnemonic(entropyBitsSize);
        return mnemonicToString(mnemonicWords);
    }

    public static String mnemonicToString(List<String> mnemonicWords) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mnemonicWords.size(); i++) {
            if (i != 0) sb.append(' ');
            sb.append(mnemonicWords.get(i));
        }
        return sb.toString();
    }

    static String generateRandomId() {
        byte[] randomIdBytes = new byte[32];
        SecureRandom sr = new SecureRandom();
        sr.nextBytes(randomIdBytes);
        return Hex.toHexString(randomIdBytes);
    }

    public WalletAccount createAccount(CoinType coin, @Nullable KeyParameter key) {
        return createAccount(coin, false, key);
    }

    public WalletAccount createAccount(CoinType coin, boolean generateAllKeys,
                                  @Nullable KeyParameter key) {
        return createAccounts(Lists.newArrayList(coin), generateAllKeys, key).get(0);
    }

    public List<WalletAccount> createAccounts(List<CoinType> coins, boolean generateAllKeys,
                                  @Nullable KeyParameter key) {
        lock.lock();
        try {
            ImmutableList.Builder<WalletAccount> newAccounts = ImmutableList.builder();
            for (CoinType coin : coins) {
                log.info("Creating coin pocket for {}", coin);
                WalletAccount newAccount = createAndAddAccount(coin, key);
                if (generateAllKeys) {
                    newAccount.maybeInitializeAllKeys();
                }
                newAccounts.add(newAccount);
            }
            return newAccounts.build();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if at least one account exists for a specific coin
     */
    public boolean isAccountExists(CoinType coinType) {
        lock.lock();
        try {
            return accountsByType.containsKey(coinType);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if account exists
     */
    public boolean isAccountExists(@Nullable String accountId) {
        if (accountId == null) return false;
        lock.lock();
        try {
            return accounts.containsKey(accountId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get a specific account, null if does not exist
     */
    @Nullable
    public WalletAccount getAccount(@Nullable String accountId) {
        if (accountId == null) return null;
        lock.lock();
        try {
            return accounts.get(accountId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get accounts for a specific coin type. Returns empty list if no account exists
     */
    public List<WalletAccount> getAccounts(CoinType coinType) {
        return getAccounts(Lists.newArrayList(coinType));
    }

    /**
     * Get accounts for a specific coin type. Returns empty list if no account exists
     */
    public List<WalletAccount> getAccounts(List<CoinType> types) {
        lock.lock();
        try {
            ImmutableList.Builder<WalletAccount> builder = ImmutableList.builder();
            for (CoinType type : types) {
                if (isAccountExists(type)) {
                    builder.addAll(accountsByType.get(type));
                }
            }
            return builder.build();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get accounts that watch a specific address. Returns empty list if no account exists
     */
    public List<WalletAccount> getAccounts(final AbstractAddress address) {
        lock.lock();
        try {
            ImmutableList.Builder<WalletAccount> builder = ImmutableList.builder();
            CoinType type = address.getType();
            if (isAccountExists(type)) {
                for (WalletAccount account : accountsByType.get(type)) {
                    if (account.isAddressMine(address)) {
                        builder.add(account);
                    }
                }
            }
            return builder.build();
        } finally {
            lock.unlock();
        }
    }

    public List<WalletAccount> getAllAccounts() {
        lock.lock();
        try {
            return ImmutableList.copyOf(accounts.values());
        } finally {
            lock.unlock();
        }
    }


    public List getAccountIds() {
        lock.lock();
        try {
            return ImmutableList.copyOf(accounts.keySet());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Generate and add a new BIP44 account for a specific coin type
     */
    private WalletAccount createAndAddAccount(CoinType coinType, @Nullable KeyParameter key) {
        checkState(lock.isHeldByCurrentThread(), "Lock is held by another thread");
        checkNotNull(coinType, "Attempting to create a pocket for a null coin");

        // TODO, currently we support a single account so return the existing account
        List<WalletAccount> currentAccount = getAccounts(coinType);
        if (currentAccount.size() > 0) {
            return currentAccount.get(0);
        }
        // TODO ///////////////

        DeterministicHierarchy hierarchy;
        if (isEncrypted()) {
            hierarchy = new DeterministicHierarchy(masterKey.decrypt(getKeyCrypter(), key));
        } else {
            hierarchy= new DeterministicHierarchy(masterKey);
        }
        int newIndex = getLastAccountIndex(coinType) + 1;
        DeterministicKey rootKey = hierarchy.get(coinType.getBip44Path(newIndex), false, true);

        WalletAccount newPocket;

        if (coinType instanceof BitFamily) {
            newPocket = new WalletPocketHD(rootKey, coinType, getKeyCrypter(), key);
        } else if (coinType instanceof NxtFamily) {
            newPocket = new NxtFamilyWallet(rootKey, coinType, getKeyCrypter(), key);
        } else {
            throw new UnsupportedCoinTypeException(coinType);
        }

        if (isEncrypted() && !newPocket.isEncrypted()) {
            newPocket.encrypt(getKeyCrypter(), key);
        }
        addAccount(newPocket);
        return newPocket;
    }

    /**
     * Get the last BIP44 account index of an account in this wallet. If no accounts found return -1
     */
    private int getLastAccountIndex(CoinType coinType) {
        if (!isAccountExists(coinType)) {
            return -1;
        }
        int lastIndex = -1;
        for (WalletAccount account : accountsByType.get(coinType)) {
            if (account instanceof WalletPocketHD) {
                int index = ((WalletPocketHD) account).getAccountIndex();
                if (index > lastIndex) {
                    lastIndex = index;
                }
            }
        }
        return lastIndex;
    }

    public void addAccount(WalletAccount pocket) {
        lock.lock();
        try {
            String id = pocket.getId();
            CoinType type = pocket.getCoinType();

            checkState(!accounts.containsKey(id), "Cannot replace an existing wallet pocket");

            if (!accountsByType.containsKey(type)) {
                accountsByType.put(type, new ArrayList<WalletAccount>());
            }
            accountsByType.get(type).add(pocket);
            accounts.put(pocket.getId(), pocket);
            pocket.setWallet(this);
        } finally {
            lock.unlock();
        }
    }

    public WalletAccount deleteAccount(String id) {
        lock.lock();
        try {
            if (!accounts.containsKey(id)) {
                return null;
            }

            WalletAccount deletedAccount = accounts.remove(id);
            CoinType type = deletedAccount.getCoinType();
            ArrayList<WalletAccount> sameTypeAccounts = accountsByType.get(type);
            if (sameTypeAccounts != null) {
                if (!sameTypeAccounts.remove(deletedAccount)) {
                    log.warn("Could not find account in accounts by type index");
                }
                if (sameTypeAccounts.size() == 0) {
                    accountsByType.remove(type);
                }
            }
            deletedAccount.setWallet(null);
            deletedAccount.disconnect();
            saveNow();
            return deletedAccount;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Make the wallet generate all the needed lookahead keys if needed
     */
    public void maybeInitializeAllPockets() {
        lock.lock();
        try {
            for (WalletAccount account : accounts.values()) {
                if (account instanceof WalletPocketHD) {
                    account.maybeInitializeAllKeys();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    //TODO remove public and implement seed password protection check
    public DeterministicKey getMasterKey() {
        lock.lock();
        try {
            return masterKey;
        } finally {
            lock.unlock();
        }
    }

    /** Returns a list of words that represent the seed or null if this chain is a watching chain. */
    @Nullable
    public List<String> getMnemonicCode() {
        if (seed == null) return null;

        lock.lock();
        try {
            return seed.getMnemonicCode();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public DeterministicSeed getSeed() {
        lock.lock();
        try {
            return seed;
        } finally {
            lock.unlock();
        }
    }

    /** Returns the {@link KeyCrypter} in use or null if the key chain is not encrypted. */
    @Nullable
    public KeyCrypter getKeyCrypter() {
        lock.lock();
        try {
            return masterKey.getKeyCrypter();
        } finally {
            lock.unlock();
        }
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public WalletAccount refresh(String accountIdToReset) {
        lock.lock();
        try {
            WalletAccount account = getAccount(accountIdToReset);
            if (account != null) {
                account.refresh();
                saveLater();
            }
            return account;
        } finally {
            lock.unlock();
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Serialization support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    Protos.Wallet toProtobuf() {
        lock.lock();
        try {
            return WalletProtobufSerializer.toProtobuf(this);
        } finally {
            lock.unlock();
        }
    }
    /**
     * Returns a wallet deserialized from the given file.
     */
    public static Wallet loadFromFile(File f) throws UnreadableWalletException {
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(f);
                return loadFromFileStream(stream);
            } finally {
                if (stream != null) stream.close();
            }
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not open file", e);
        }
    }

    /**
     * Returns a wallet deserialized from the given input stream.
     */
    public static Wallet loadFromFileStream(InputStream stream) throws UnreadableWalletException {
        return WalletProtobufSerializer.readWallet(stream);
    }

    /**
     * Uses protobuf serialization to save the wallet to the given file stream. To learn more about this file format, see
     * {@link WalletProtobufSerializer}.
     */
    public void saveToFileStream(OutputStream f) throws IOException {
        lock.lock();
        try {
            WalletProtobufSerializer.writeWallet(this, f);
        } finally {
            lock.unlock();
        }
    }

    /** Saves the wallet first to the given temp file, then renames to the dest file. */
    public void saveToFile(File temp, File destFile) throws IOException {
        FileOutputStream stream = null;
        lock.lock();
        try {
            stream = new FileOutputStream(temp);
            saveToFileStream(stream);
            // Attempt to force the bits to hit the disk. In reality the OS or hard disk itself may still decide
            // to not write through to physical media for at least a few seconds, but this is the best we can do.
            stream.flush();
            stream.getFD().sync();
            stream.close();
            stream = null;
            if (!temp.renameTo(destFile)) {
                throw new IOException("Failed to rename " + temp + " to " + destFile);
            }
        } catch (RuntimeException e) {
            log.error("Failed whilst saving wallet", e);
            throw e;
        } finally {
            lock.unlock();
            if (stream != null) {
                stream.close();
            }
            if (temp.exists()) {
                log.warn("Temp file still exists after failed save.");
            }
        }
    }

    /**
     * <p>Sets up the wallet to auto-save itself to the given file, using temp files with atomic renames to ensure
     * consistency. After connecting to a file, you no longer need to save the wallet manually, it will do it
     * whenever necessary. Protocol buffer serialization will be used.</p>
     *
     * <p>If delayTime is set, a background thread will be created and the wallet will only be saved to
     * disk every so many time units. If no changes have occurred for the given time period, nothing will be written.
     * In this way disk IO can be rate limited. It's a good idea to set this as otherwise the wallet can change very
     * frequently, eg if there are a lot of transactions in it or during block sync, and there will be a lot of redundant
     * writes. Note that when a new key is added, that always results in an immediate save regardless of
     * delayTime. <b>You should still save the wallet manually when your program is about to shut down as the JVM
     * will not wait for the background thread.</b></p>
     *
     * <p>An event listener can be provided. If a delay >0 was specified, it will be called on a background thread
     * with the wallet locked when an auto-save occurs. If delay is zero or you do something that always triggers
     * an immediate save, like adding a key, the event listener will be invoked on the calling threads.</p>
     *
     * @param f The destination file to save to.
     * @param delayTime How many time units to wait until saving the wallet on a background thread.
     * @param timeUnit the unit of measurement for delayTime.
     * @param eventListener callback to be informed when the auto-save thread does things, or null
     */
    public WalletFiles autosaveToFile(File f, long delayTime, TimeUnit timeUnit,
                                      @Nullable WalletFiles.Listener eventListener) {
        lock.lock();
        try {
            checkState(vFileManager == null, "Already auto saving this wallet.");
            WalletFiles manager = new WalletFiles(this, f, delayTime, timeUnit);
            if (eventListener != null) {
                manager.setListener(eventListener);
            }
            vFileManager = manager;
            return manager;
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>
     * Disables auto-saving, after it had been enabled with
     * {@link Wallet#autosaveToFile(java.io.File, long, java.util.concurrent.TimeUnit, com.coinomi.core.wallet.WalletFiles.Listener)}
     * before. This method blocks until finished.
     * </p>
     */
    public void shutdownAutosaveAndWait() {
        lock.lock();
        try {
            WalletFiles files = vFileManager;
            vFileManager = null;
            if (files != null) {
                files.shutdownAndWait();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Requests an asynchronous save on a background thread */
    public void saveLater() {
        lock.lock();
        try {
            WalletFiles files = vFileManager;
            if (files != null) {
                files.saveLater();
            }
        } finally {
            lock.unlock();
        }
    }

    /** If auto saving is enabled, do an immediate sync write to disk ignoring any delays. */
    public void saveNow() {
        lock.lock();
        try {
            WalletFiles files = vFileManager;
            if (files != null) {
                try {
                    files.saveNow();  // This calls back into saveToFile().
                } catch (IOException e) {
                    // Can't really do much at this point, just let the API user know.
                    log.error("Failed to save wallet to disk!", e);
                    Thread.UncaughtExceptionHandler handler = Threading.uncaughtExceptionHandler;
                    if (handler != null)
                        handler.uncaughtException(Thread.currentThread(), e);
                }
            }
        } finally {
            lock.unlock();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Encryption support
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean isEncrypted() {
        lock.lock();
        try {
            return masterKey.isEncrypted();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Encrypt the keys in the group using the KeyCrypter and the AES key. A good default KeyCrypter to use is
     * {@link org.bitcoinj.crypto.KeyCrypterScrypt}.
     *
     * @throws org.bitcoinj.crypto.KeyCrypterException Thrown if the wallet encryption fails for some reason,
     *         leaving the group unchanged.
     */
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        checkNotNull(keyCrypter, "Attempting to encrypt with a null KeyCrypter");
        checkNotNull(aesKey, "Attempting to encrypt with a null KeyParameter");

        lock.lock();
        try {
            if (seed != null) seed = seed.encrypt(keyCrypter, aesKey);
            masterKey = masterKey.encrypt(keyCrypter, aesKey, null);

            for (WalletAccount account : accounts.values()) {
                if (account.isEncryptable()) {
                    account.encrypt(keyCrypter, aesKey);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /* package */ void decrypt(KeyParameter aesKey) {
        checkNotNull(aesKey, "Attemting to decrypt with a null KeyParameter");

        lock.lock();
        try {
            checkState(isEncrypted(), "Wallet is already decrypted");

            if (seed != null) {
                checkState(seed.isEncrypted(), "Seed is already decrypted");
                List<String> mnemonic = null;
                try {
                    mnemonic = decodeMnemonicCode(getKeyCrypter().decrypt(seed.getEncryptedData(), aesKey));
                } catch (UnreadableWalletException e) {
                    throw new RuntimeException(e);
                }
                seed = new DeterministicSeed(new byte[16], mnemonic, 0);
            }

            masterKey = masterKey.decrypt(getKeyCrypter(), aesKey);

            for (WalletAccount account : accounts.values()) {
                if (account.isEncryptable()) {
                    account.decrypt(aesKey);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private static List<String> decodeMnemonicCode(byte[] mnemonicCode) throws UnreadableWalletException {
        try {
            return Splitter.on(" ").splitToList(new String(mnemonicCode, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new UnreadableWalletException(e.toString());
        }
    }

    public List<Value> getBalances() {
        ImmutableList.Builder<Value> builder = ImmutableList.builder();
        lock.lock();
        try {
            for (WalletAccount account : accounts.values()) {
                builder.add(account.getBalance());
            }
            return builder.build();
        } finally {
            lock.unlock();
        }
    }

    public boolean isLoading() {
        for (WalletAccount account : accounts.values()) {
            if (account.isLoading()) {
                return true;
            }
        }
        return false;
    }

    // TODO
//    public void broadcastTx(SendRequest request) throws IOException {
//        getPocket(request.type).broadcastTx(request.tx);
//    }
}
