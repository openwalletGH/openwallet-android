package com.coinomi.core.uri;

import com.coinomi.core.uri.CoinURIParseException;

/**
 * <p>This exception occurs when a required field is detected (under the BIP21 rules) and fails
 * to pass the associated test (such as {@code req-expires} being out of date), or the required field is unknown
 * to this version of the client in which case it should fail for security reasons.</p>
 *
 * @since 0.3.0
 *         
 */
public class RequiredFieldValidationException extends CoinURIParseException {

    public RequiredFieldValidationException(String s) {
        super(s);
    }

    public RequiredFieldValidationException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
