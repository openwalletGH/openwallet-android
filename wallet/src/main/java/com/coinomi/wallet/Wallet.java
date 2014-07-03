package com.coinomi.wallet;

import com.coinomi.wallet.coins.Coin;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.crypto.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Giannis Dzegoutanis
 */
final public class Wallet {
    private static final Logger log = LoggerFactory.getLogger(Wallet.class);
    private final DeterministicKey rootKey;
    private final DeterministicHierarchy bip44Hierarchy;
    private int lastBlockSeenHeight;

    private ConcurrentHashMap<String, String> addressStatus =
            new ConcurrentHashMap<String, String>();

    private int account = 0;
    String BIP_44_KEY_PATH = "44'/%d'/%d'/%d/%d";

    private static final int EXTERNAL_ADDRESS_INDEX = 0;
    private static final int INTERNAL_ADDRESS_INDEX = 1;

    public Wallet(List<String> mnemonic) throws IOException, MnemonicException {
        // TODO
        rootKey = null;
        bip44Hierarchy = null;

//        MnemonicCode mc = new MnemonicCode();
//        mc.check(mnemonic);
//
//        DeterministicSeed seed = new DeterministicSeed(mnemonic, 0);
//        rootKey = HDKeyDerivation.createMasterPrivateKey(seed.getSecretBytes());
//        // this is /44'/ path
//        bip44Hierarchy = new DeterministicHierarchy(HDKeyDerivation.deriveChildKey(rootKey, new ChildNumber(44, true)));
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

    private List<ChildNumber> getPath(Coin coin, int chain, int keyIndex) {
        String path = String.format(BIP_44_KEY_PATH, coin.getBip44Index(), account, chain, keyIndex);
        return HDUtils.parsePath(path);
    }

    public DeterministicKey getExternalKey(Coin coin, int keyIndex) {
        List<ChildNumber> path = getPath(coin, EXTERNAL_ADDRESS_INDEX, keyIndex);
        return bip44Hierarchy.get(path, false, true);
    }


    public Address getExternalAddress(Coin coin, int keyIndex) {
        DeterministicKey key = getExternalKey(coin, keyIndex);
        return key.toAddress(coin.getNetworkParams());
    }


    public DeterministicKey getInternalKey(Coin coin, int keyIndex) {
        List<ChildNumber> path = getPath(coin, INTERNAL_ADDRESS_INDEX, keyIndex);
        return bip44Hierarchy.get(path, false, true);
    }

    public Address getInternalAddress(Coin coin, int keyIndex) {
        DeterministicKey key = getExternalKey(coin, keyIndex);
        return key.toAddress(coin.getNetworkParams());
    }

    public boolean statusChanged(Coin coin, String address, String lastStatus) {
        if (addressStatus.contains(address)) {
            return addressStatus.get(address).equals(lastStatus);
        }
        else {
            return true; // If there is no
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
}
