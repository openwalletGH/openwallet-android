package com.coinomi.core.uri;

/**
 * <p>This exception occurs when an optional field is detected (under the Bitcoin URI scheme) and fails
 * to pass the associated test (such as {@code amount} not being a valid number).</p>
 *
 * @since 0.3.0
 *         
 */
public class OptionalFieldValidationException extends CoinURIParseException {

    public OptionalFieldValidationException(String s) {
        super(s);
    }

    public OptionalFieldValidationException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
