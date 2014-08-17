package com.coinomi.core.wallet;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.network.BlockchainConnection;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.crypto.DeterministicHierarchy;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.HDKeyDerivation;
import com.google.bitcoin.crypto.MnemonicCode;
import com.google.bitcoin.crypto.MnemonicException;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.wallet.DeterministicSeed;
import com.google.common.collect.ImmutableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * @author Giannis Dzegoutanis
 */
final public class Wallet implements ConnectionEventListener {
    private static final Logger log = LoggerFactory.getLogger(Wallet.class);

    private final ReentrantLock lock = Threading.lock("KeyChain");

    @GuardedBy("lock") private final LinkedHashMap<CoinType, WalletPocket> pockets;

    private final DeterministicKey masterKey;

    private int lastBlockSeenHeight;


    private final static int ACCOUNT_ZERO = 0;

    @Nullable transient BlockchainConnection blockchainConnection;

    public Wallet(List<String> mnemonic) throws IOException, MnemonicException {
        this(mnemonic, "");
    }

    public Wallet(List<String> mnemonic, String password) throws IOException, MnemonicException {
        new MnemonicCode().check(mnemonic);



        DeterministicSeed seed = new DeterministicSeed(mnemonic, "", 0);
//        masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
        masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());

        pockets = new LinkedHashMap<CoinType, WalletPocket>();
    }

    public static List<String> generateMnemonic() throws IOException {
        byte[] entropy = new byte[16];

        SecureRandom sr = new SecureRandom();
        sr.nextBytes(entropy);

        MnemonicCode mc = new MnemonicCode();
        List<String> mnemonic = null;
        try {
            mnemonic = mc.toMnemonic(entropy);
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException(e); // should not happen, we have 16bytes of entropy
        }

        return mnemonic;
    }

//    private List<ChildNumber> getPath(CoinType coinType, int chain, int keyIndex) {
//        String path = String.format(BIP_44_KEY_PATH, coinType.getBip44Index(), ACCOUNT_ZERO, chain, keyIndex);
//        return HDUtils.parsePath(path);
//    }

//    public DeterministicKey getExternalKey(CoinType parameters, int keyIndex) {
//        List<ChildNumber> path = getPath(parameters, EXTERNAL_ADDRESS_INDEX, keyIndex);
//        return bip44Hierarchy.get(path, false, true);
//    }


//    public Address getExternalAddress(CoinType parameters, int keyIndex) {
//        DeterministicKey key = getExternalKey(parameters, keyIndex);
//        return key.toAddress(parameters);
//    }


//    public DeterministicKey getInternalKey(CoinType parameters, int keyIndex) {
//        List<ChildNumber> path = getPath(parameters, INTERNAL_ADDRESS_INDEX, keyIndex);
//        return bip44Hierarchy.get(path, false, true);
//    }

//    public Address getInternalAddress(CoinType parameters, int keyIndex) {
//        DeterministicKey key = getExternalKey(parameters, keyIndex);
//        return key.toAddress(parameters);
//    }

//    List<Address> getKeyToWatch(CoinType parameters) {
//        KeyChain chain = getPocket(parameters);
//
//
//        chain.getKeys();
//    }

    public void createCoinPockets(List<CoinType> coins) {
        for (CoinType coin : coins) {
            log.info("Creating coin pocket for {}", coin);
            getPocket(coin);
        }
    }

    public WalletPocket getPocket(CoinType coinType) {
        lock.lock();
        try {

            if (!pockets.containsKey(coinType)) {
                createPocket(coinType);
            }
            return pockets.get(coinType);
        } finally {
            lock.unlock();
        }
    }

    private void createPocket(CoinType coinType) {
        DeterministicHierarchy hierarchy = new DeterministicHierarchy(masterKey);
        DeterministicKey rootKey = hierarchy.get(coinType.getBip44Path(ACCOUNT_ZERO), false, true);
        pockets.put(coinType, new WalletPocket(rootKey, coinType));
    }

    List<CoinType> getCoinTypes() {
        lock.lock();
        try {
            return ImmutableList.copyOf(pockets.keySet());
        } finally {
            lock.unlock();
        }
    }


    public int getLastBlockSeenHeight() {
        //TODO
        return lastBlockSeenHeight;
    }

    public Transaction getTransaction(Sha256Hash hash) {
        //TODO
        return null;
    }

    public void saveToFile(File walletFile) {
        //TODO
    }

    @Override
    public void onConnection(BlockchainConnection blockchainConnection) {
        this.blockchainConnection = blockchainConnection;
        for (CoinType coin : getCoinTypes()) {
            WalletPocket pocket = getPocket(coin);
            if (blockchainConnection != null) {
                pocket.onConnection(blockchainConnection);
            }
        }
    }

    @Override
    public void onDisconnect() {
        this.blockchainConnection = null;
    }


    public void sendCoins(Address address, Coin amount) throws InsufficientMoneyException {
        WalletPocket pocket = getPocket((CoinType) address.getParameters());

        pocket.sendCoins(address, amount);
    }
}
