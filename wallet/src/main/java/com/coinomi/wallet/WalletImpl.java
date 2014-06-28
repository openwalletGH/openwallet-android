package com.coinomi.wallet;

import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.crypto.MnemonicCode;
import com.google.bitcoin.crypto.MnemonicException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
public class WalletImpl implements Wallet {
    private static final Logger log = LoggerFactory.getLogger(WalletImpl.class);
    private int lastBlockSeenHeight;

    public WalletImpl(List<String> mnemonic) throws IOException, MnemonicException {
//        MnemonicCode mc = new MnemonicCode();
//        mc.check(mnemonic);
//
//        DeterministicSeed seed = new DeterministicSeed(mnemonic, 0);
//        DeterministicKey rootKey = HDKeyDerivation.createMasterPrivateKey(seed.getSecretBytes());
//        System.out.println(rootKey.serializePrivB58());
//
//        DeterministicHierarchy hierarchy = new DeterministicHierarchy(rootKey);
//
//        ImmutableList<ChildNumber> BITCOIN_TESTNET = ImmutableList.of(new ChildNumber(44, true), new ChildNumber(1, true), new ChildNumber(0, true));
//        DeterministicKey key = hierarchy.get(BITCOIN_TESTNET, false, true);
//
//
////
//        DeterministicKey externalKey = hierarchy.deriveChild(BITCOIN_TESTNET, false, false, ChildNumber.ZERO);
//        DeterministicKey internalKey = hierarchy.deriveChild(BITCOIN_TESTNET, false, false, ChildNumber.ONE);
//
//        System.out.println(externalKey.toString());
////		for (ChildNumber c : externalKey.getPath()) {
////			System.out.print(c.toString());
////		}
//
//        DeterministicKey k1 = HDKeyDerivation.deriveChildKey(externalKey, new ChildNumber(0, false));
//        System.out.println(k1);
//        DeterministicKey k2 = HDKeyDerivation.deriveChildKey(externalKey, new ChildNumber(1, false));
//        System.out.println(k2);


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

    @Override
    public int getLastBlockSeenHeight() {
        //TODO
        return lastBlockSeenHeight;
    }

    @Override
    public Transaction getTransaction(Sha256Hash hash) {
        //TODO
        return null;
    }

    @Override
    public void saveToFile(File walletFile) {
        //TODO
    }
}
