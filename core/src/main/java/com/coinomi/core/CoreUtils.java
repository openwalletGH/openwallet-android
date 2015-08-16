package com.coinomi.core;

import com.coinomi.core.wallet.AbstractAddress;
import com.google.common.base.Joiner;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John L. Jegutanis
 */
public class CoreUtils {

    public static String getMnemonicToString(List<String> mnemonic) {
        return Joiner.on(' ').join(mnemonic);
    }

    public static List<String> bytesToMnemonic(byte[] bytes) {
        List<String> mnemonic;
        try {
            mnemonic = MnemonicCode.INSTANCE.toMnemonic(bytes);
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException(e); // should not happen, we have 16bytes of entropy
        }
        return mnemonic;
    }

    public static String bytesToMnemonicString(byte[] bytes) {
        return getMnemonicToString(bytesToMnemonic(bytes));
    }

    public static ArrayList<String> parseMnemonic(String mnemonicString) {
        ArrayList<String> seedWords = new ArrayList<>();
        for (String word : mnemonicString.trim().split(" ")) {
            if (word.isEmpty()) continue;
            seedWords.add(word);
        }
        return seedWords;
    }

}
