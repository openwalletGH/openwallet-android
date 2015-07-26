package com.coinomi.wallet.util;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 * @author John L. Jegutanis
 *
 * This class checks the quality of the provided password, for example if it is too common.
 *
 * The common passwords list is provided by Mark Burnett
 * https://xato.net/passwords/more-top-worst-passwords/
 */
public class PasswordQualityChecker {
    private static final Logger log = LoggerFactory.getLogger(PasswordQualityChecker.class);
    public static final int DEFAULT_MIN_PASSWORD_LENGTH = 10;
    private static final String COMMON_PASSWORDS_TXT = "common_passwords.txt";
    private final HashSet<String> passwordList;
    private final int minPasswordLength;

    public PasswordQualityChecker(Context context) {
        this(context, DEFAULT_MIN_PASSWORD_LENGTH);
    }

    public PasswordQualityChecker(Context context, int minPassLength) {
        this.minPasswordLength = minPassLength;
        this.passwordList = new HashSet<>(10000);

        try {
            InputStream stream = context.getAssets().open(COMMON_PASSWORDS_TXT);
            BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String word;
            while ((word = br.readLine()) != null) {
                this.passwordList.add(word);
            }
            br.close();
        } catch (IOException e) {
            log.error("Could not open common passwords file.", e);
        }
    }

    /**
     * Check if the password meets some quality criteria, like length and how common it is
     */
    public void checkPassword(String password) throws PasswordTooCommonException, PasswordTooShortException {
        if (passwordList.contains(password)) throw new PasswordTooCommonException(password);

        if (password.length() < minPasswordLength) {
            throw new PasswordTooShortException("Password length is too short: " + password.length());
        }
    }

    /**
     * Gets the password list this code uses.
     */
    public Set<String> getWordList() {
        return passwordList;
    }

    public int getMinPasswordLength() {
        return minPasswordLength;
    }

    public class PasswordTooCommonException extends Exception {
        public PasswordTooCommonException(String commonPassword) {
            super(commonPassword);
        }
    }

    public class PasswordTooShortException extends Exception {
        public PasswordTooShortException(String detailMessage) {
            super(detailMessage);
        }
    }
}
