/*
 * Copyright 2012, 2014 the original author or authors.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.coinomi.core.uri;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.families.NxtFamily;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.families.bitcoin.BitAddress;
import com.coinomi.core.wallet.families.nxt.NxtAddress;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * <p>Provides a standard implementation of a Bitcoin URI with support for the following:</p>
 *
 * <ul>
 * <li>URLEncoded URIs (as passed in by IE on the command line)</li>
 * <li>BIP21 names (including the "req-" prefix handling requirements)</li>
 * </ul>
 *
 * <h2>Accepted formats</h2>
 *
 * <p>The following input forms are accepted:</p>
 *
 * <ul>
 * <li>{@code bitcoin:<address>}</li>
 * <li>{@code bitcoin:<address>?<name1>=<value1>&<name2>=<value2>} with multiple
 * additional name/value pairs</li>
 * </ul>
 *
 * <p>The name/value pairs are processed as follows.</p>
 * <ol>
 * <li>URL encoding is stripped and treated as UTF-8</li>
 * <li>names prefixed with {@code req-} are treated as required and if unknown or conflicting cause a parse exception</li>
 * <li>Unknown names not prefixed with {@code req-} are added to a Map, accessible by parameter name</li>
 * <li>Known names not prefixed with {@code req-} are processed unless they are malformed</li>
 * </ol>
 *
 * <p>The following names are known and have the following formats:</p>
 * <ul>
 * <li>{@code amount} decimal value to 8 dp (e.g. 0.12345678) <b>Note that the
 * exponent notation is not supported any more</b></li>
 * <li>{@code label} any URL encoded alphanumeric</li>
 * <li>{@code message} any URL encoded alphanumeric</li>
 * </ul>
 * 
 * @author Andreas Schildbach (initial code)
 * @author Jim Burton (enhancements for MultiBit)
 * @author Gary Rowe (BIP21 support)
 * @author John L. Jegutanis
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki">BIP 0021</a>
 */
public class CoinURI implements Serializable {
    /**
     * Provides logging for this class
     */
    private static final Logger log = LoggerFactory.getLogger(CoinURI.class);

    // Not worth turning into an enum
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_LABEL = "label";
    public static final String FIELD_AMOUNT = "amount";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_PAYMENT_REQUEST_URL = "r";
    public static final String FIELD_ADDRESS_REQUEST_URI = "req-addressrequest";
    public static final String FIELD_NETWORK = "req-network";
    public static final String FIELD_PUBKEY = "pubkey";

    private static final String ENCODED_SPACE_CHARACTER = "%20";
    private static final String AMPERSAND_SEPARATOR = "&";
    private static final String QUESTION_MARK_SEPARATOR = "?";

    @Nullable
    private CoinType type;

    /**
     * Contains all the parameters in the order in which they were processed
     */
    private final Map<String, Object> parameterMap = new LinkedHashMap<String, Object>();

    /**
     * Constructs a new CoinURI from the given string. Can be for any network.
     *
     * @param uri The raw URI data to be parsed (see class comments for accepted formats)
     * @throws CoinURIParseException if the URI is not syntactically or semantically valid.
     */
    public CoinURI(String uri) throws CoinURIParseException {
        this(null, uri);
    }

    /**
     * Constructs a new object by trying to parse the input as a valid coin URI.
     *
     * @param uriType The network parameters that determine which network the URI is from, or null if you don't have
     *               any expectation about what network the URI is for and wish to check yourself.
     * @param input The raw URI data to be parsed (see class comments for accepted formats)
     *
     * @throws CoinURIParseException If the input fails coin URI syntax and semantic checks.
     */
    public CoinURI(@Nullable CoinType uriType, String input) throws CoinURIParseException {
        checkNotNull(input);
        log.debug("Attempting to parse '{}' for {}", input, uriType == null ? "any" : uriType.getId());

        // If type is null we will try to heuristically find it later
        type = uriType;

        // Attempt to form the URI (fail fast syntax checking to official standards).
        URI uri;
        uri = getUri(input);

        // URI is formed as  bitcoin:<address>?<query parameters>
        // blockchain.info generates URIs of non-BIP compliant form bitcoin://address?....
        // We support both until Ben fixes his code.
        
        // Remove the bitcoin scheme.
        // (Note: getSchemeSpecificPart() is not used as it unescapes the label and parse then fails.
        // For instance with : bitcoin:129mVqKUmJ9uwPxKJBnNdABbuaaNfho4Ha?amount=0.06&label=Tom%20%26%20Jerry
        // the & (%26) in Tom and Jerry gets interpreted as a separator and the label then gets parsed
        // as 'Tom ' instead of 'Tom & Jerry')
        String schemeSpecificPart;
        String uriScheme;
        List<CoinType> possibleTypes;

        if (type == null) {
            if (uri.getScheme() != null) {
                try {
                    possibleTypes = CoinID.fromUriScheme(uri.getScheme());
                    uriScheme = possibleTypes.get(0).getUriScheme();
                } catch (IllegalArgumentException e) {
                    throw new CoinURIParseException("Unsupported URI scheme: " + uri.getScheme());
                }
            } else {
                throw new CoinURIParseException("Unrecognisable URI format: " + input);
            }
        } else {
            uriScheme = type.getUriScheme();
            possibleTypes = Lists.newArrayList(type);
        }

        String inputLowercase = input.toLowerCase();
        if (inputLowercase.startsWith(uriScheme + "://")) {
            schemeSpecificPart = input.substring((uriScheme + "://").length());
        } else if (inputLowercase.startsWith(uriScheme + ":")) {
            schemeSpecificPart = input.substring((uriScheme + ":").length());
        } else {
            throw new CoinURIParseException("Unsupported URI scheme: " + uri.getScheme());
        }

        // Split off the address from the rest of the query parameters.
        String[] addressSplitTokens = schemeSpecificPart.split("\\?");
        if (addressSplitTokens.length == 0)
            throw new CoinURIParseException("No data found after the " + uriScheme + ": prefix");
        String addressToken = addressSplitTokens[0];  // may be empty!

        String[] nameValuePairTokens;
        if (addressSplitTokens.length == 1) {
            // Only an address is specified - use an empty '<name>=<value>' token array.
            nameValuePairTokens = new String[] {};
        } else {
            if (addressSplitTokens.length == 2) {
                // Split into '<name>=<value>' tokens.
                nameValuePairTokens = addressSplitTokens[1].split("&");
            } else {
                throw new CoinURIParseException("Too many question marks in URI '" + uri + "'");
            }
        }

        // Parse the address if any and set type
        if (!addressToken.isEmpty()) {
            // Attempt to parse the addressToken as a possible type address
            AbstractAddress address = null;
            for (CoinType possibleType : possibleTypes) {
                if (possibleType instanceof NxtFamily) {
                    try {
                        address = new NxtAddress(possibleType, addressToken);
                        putWithValidation(FIELD_ADDRESS, address);
                        break;
                    } catch (RuntimeException e) {  /* continue */ }
                } else {
                    try {
                        address = BitAddress.from(possibleType, addressToken);
                        putWithValidation(FIELD_ADDRESS, address);
                        break;
                    } catch (final AddressMalformedException e) { /* continue */ }
                }
            }

            if (address == null) {
                throw new CoinURIParseException("Bad address: " + addressToken);
            }

            if (type == null){
                type = address.getType();
            } else {
                checkState(type.equals(address.getType()));
            }
        }

        // Attempt to parse the rest of the URI parameters.
        parseParameters(nameValuePairTokens, possibleTypes);

        // If until now we haven't found the coin type and the URI is an Address Request, use the default type
        if (type == null && isAddressRequest()) {
            type = possibleTypes.get(0);
        }

        if (!addressToken.isEmpty() && isAddressRequest()) {
            throw new CoinURIParseException("Cannot set an address when requesting an address");
        }

        if (addressToken.isEmpty() && getPaymentRequestUrl() == null && getAddressRequestUri() == null) {
            throw new CoinURIParseException("No address and no r= parameter found");
        }
    }

    private static URI getUri(String input) throws CoinURIParseException {
        URI uri;
        try {
            uri = new URI(input);
        } catch (URISyntaxException e) {
            throw new CoinURIParseException("Bad URI syntax", e);
        }
        return uri;
    }



    /**
     * @param nameValuePairTokens The tokens representing the name value pairs (assumed to be
     *                            separated by '=' e.g. 'amount=0.2')
     * @param possibleTypes
     */
    private void parseParameters(String[] nameValuePairTokens, List<CoinType> possibleTypes) throws CoinURIParseException {
        // Attempt to decode the rest of the tokens into a parameter map.
        for (String nameValuePairToken : nameValuePairTokens) {
            final int sepIndex = nameValuePairToken.indexOf('=');
            if (sepIndex == -1)
                throw new CoinURIParseException("Malformed Bitcoin URI - no separator in '" +
                        nameValuePairToken + "'");
            if (sepIndex == 0)
                throw new CoinURIParseException("Malformed Bitcoin URI - empty name '" +
                        nameValuePairToken + "'");
            final String nameToken = nameValuePairToken.substring(0, sepIndex).toLowerCase(Locale.ENGLISH);
            final String valueToken = nameValuePairToken.substring(sepIndex + 1);

            // Parse the amount.
            if (FIELD_AMOUNT.equals(nameToken)) {
                // Cannot parse amount for unknown coin type
                if (type == null) {
                    throw new OptionalFieldValidationException("Cannot parse amount for unknown coin type");
                }
                // Decode the amount (contains an optional decimal component to 8dp).
                try {
                    Value amount = checkNotNull(type).value(valueToken);
                    if (amount.signum() < 0) {
                        throw new OptionalFieldValidationException(String.format("'%s' Negative coins specified", valueToken));
                    }
                    putWithValidation(FIELD_AMOUNT, amount);
                } catch (IllegalArgumentException e) {
                    throw new OptionalFieldValidationException(String.format("'%s' is not a valid amount", valueToken), e);
                } catch (ArithmeticException e) {
                    throw new OptionalFieldValidationException(String.format("'%s' has too many decimal places", valueToken), e);
                }
            } else if (FIELD_ADDRESS_REQUEST_URI.equals(nameToken)) {
                try {
                    if (valueToken.length() > 0)
                        putWithValidation(nameToken, new URI(URLDecoder.decode(valueToken, "UTF-8")));
                } catch (UnsupportedEncodingException e) {
                    // Unreachable.
                    throw new RuntimeException(e);
                } catch (URISyntaxException e) {
                    throw new RequiredFieldValidationException("Address request URI '" + nameToken + "' is not valid");
                }
            } else if (FIELD_NETWORK.equals(nameToken)) {
                CoinType parsedType = null;
                if (valueToken.length() > 0) {
                    boolean isTestnet = valueToken.endsWith("test");
                    String specificType = null;
                    // If not main or test but something like nbt.main or nsr.main try to guess
                    // the network with the coin symbol although there is no guarantee that it
                    // will always work.
                    if (valueToken.length() != 4) {
                        String[] parts = valueToken.split("\\.");
                        if (parts.length != 0) specificType = parts[0];
                    }

                    for (CoinType t : possibleTypes) {
                        if (t.isTestnet() == isTestnet) {
                            // Check if a specific type is requested
                            if (specificType != null && !specificType.equalsIgnoreCase(t.getSymbol())) {
                                continue;
                            }
                            parsedType = t;
                            break;
                        }
                    }
                    if (parsedType == null) {
                        throw new RequiredFieldValidationException("Could not find network '" + valueToken + "'");
                    }
                } else {
                    // Best guess
                    parsedType = possibleTypes.get(0);
                }

                // Sanity check
                if (type != null && !type.equals(parsedType)) {
                    throw new RequiredFieldValidationException("Parsed coin type '" +
                            parsedType.getId() + "' does not match the supplied type '" + type.getId() + "'");
                }
                if (type == null) type = parsedType;
                putWithValidation(nameToken, valueToken);
            } else {
                if (nameToken.startsWith("req-")) {
                    // A required parameter that we do not know about.
                    throw new RequiredFieldValidationException("'" + nameToken + "' is required but not known, this URI is not valid");
                } else {
                    // Known fields and unknown parameters that are optional.
                    try {
                        if (valueToken.length() > 0)
                            putWithValidation(nameToken, URLDecoder.decode(valueToken, "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        // Unreachable.
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        // Note to the future: when you want to implement 'req-expires' have a look at commit 410a53791841
        // which had it in.
    }

    /**
     * Put the value against the key in the map checking for duplication. This avoids address field overwrite etc.
     * 
     * @param key The key for the map
     * @param value The value to store
     */
    private void putWithValidation(String key, Object value) throws CoinURIParseException {
        if (parameterMap.containsKey(key)) {
            throw new CoinURIParseException(String.format("'%s' is duplicated, URI is invalid", key));
        } else {
            parameterMap.put(key, value);
        }
    }

    /**
     * Same as {@link #getType()} but throws an exception if the uri has no type
     * @return The {@link com.coinomi.core.coins.CoinType} of this URI
     */
    public CoinType getTypeRequired() throws CoinURIParseException {
        if (!hasType()) {
            throw new CoinURIParseException("Unknown coin type");
        }
        return getType();
    }

    /**
     * @return The {@link com.coinomi.core.coins.CoinType} of this URI
     */
    @Nullable
    public CoinType getType() {
        return type;
    }

    public boolean hasType() {
        return type != null;
    }

    /**
     * The Bitcoin Address from the URI, if one was present. It's possible to have Bitcoin URI's with no address if a
     * r= payment protocol parameter is specified, though this form is not recommended as older wallets can't understand
     * it.
     */
    @Nullable
    public AbstractAddress getAddress() {
        return (AbstractAddress) parameterMap.get(FIELD_ADDRESS);
    }

    /**
     * @return The amount name encoded using a pure integer value based at
     *         10,000,000 units is 1 BTC. May be null if no amount is specified
     */
    public Value getAmount() {
        return (Value) parameterMap.get(FIELD_AMOUNT);
    }

    /**
     * @return The label from the URI.
     */
    public String getLabel() {
        return (String) parameterMap.get(FIELD_LABEL);
    }

    /**
     * @return The message from the URI.
     */
    public String getMessage() {
        return (String) parameterMap.get(FIELD_MESSAGE);
    }


    /**
     * @return The public key.
     */
    public String getPublicKey() {
        return (String) parameterMap.get(FIELD_PUBKEY);
    }

    /**
     * @return The URL where a payment request (as specified in BIP 70) may
     *         be fetched.
     */
    public String getPaymentRequestUrl() {
        return (String) parameterMap.get(FIELD_PAYMENT_REQUEST_URL);
    }

    public URI getAddressRequestUri() {
        return (URI) parameterMap.get(FIELD_ADDRESS_REQUEST_URI);
    }

    public boolean isAddressRequest() {
        return parameterMap.containsKey(FIELD_ADDRESS_REQUEST_URI);
    }

    public URI getAddressRequestUriResponse(AbstractAddress address) {
        return getAddressRequestUriResponse(address.toString());
    }

    public URI getAddressRequestUriResponse(String address) {
        URI uri = getAddressRequestUri();
        if (uri != null) {
            try {
                StringBuilder query = new StringBuilder();
                if (uri.getQuery() != null) {
                    query.append(uri.getQuery()).append(AMPERSAND_SEPARATOR);
                }
                query.append(FIELD_ADDRESS).append("=").append(encodeURLString(address));
                return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                        uri.getPath(), query.toString(), uri.getFragment());
            } catch (URISyntaxException e) { /* pass */ }
        }
        return null;
    }
    
    /**
     * @param name The name of the parameter
     * @return The parameter value, or null if not present
     */
    public Object getParameterByName(String name) {
        return parameterMap.get(name);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("CoinURI[");
        boolean first = true;
        for (Map.Entry<String, Object> entry : parameterMap.entrySet()) {
            if (first) {
                first = false;
            } else {
                builder.append(",");
            }
            builder.append("'").append(entry.getKey()).append("'=").append("'").append(entry.getValue().toString()).append("'");
        }
        builder.append("]");
        return builder.toString();
    }

    /**
     * Simple coin URI builder using known good fields.
     *
     * @param address The coin address
     * @return A String containing the coin URI
     */
    public static String convertToCoinURI(AbstractAddress address) {
        return convertToCoinURI(address, null, null, null, null);

    }

    /**
     * Simple coin URI builder using known good fields.
     *
     * @param address The coin address
     * @param amount The amount
     * @param label A label
     * @param message A message
     * @return A String containing the coin URI
     */
    public static String convertToCoinURI(AbstractAddress address, @Nullable Value amount,
                                          @Nullable String label, @Nullable String message) {
        return convertToCoinURI(address, amount, label, message, null);

    }

    /**
     * Simple coin URI builder using known good fields.
     *
     * @param address The coin address
     * @param amount The amount
     * @param label A label
     * @param message A message
     * @param pubkey A public key
     * @return A String containing the coin URI
     */
    public static String convertToCoinURI(AbstractAddress address, @Nullable Value amount,
                                          @Nullable String label, @Nullable String message,
                                          @Nullable String pubkey) {
        checkNotNull(address);

        CoinType type = address.getType();
        String addressStr = address.toString();

        if (amount != null && amount.signum() < 0) {
            throw new IllegalArgumentException("Coin must be positive");
        }
        
        StringBuilder builder = new StringBuilder();
        builder.append(type.getUriScheme()).append(":").append(addressStr);
        
        boolean questionMarkHasBeenOutput = false;
        
        if (amount != null) {
            builder.append(QUESTION_MARK_SEPARATOR).append(FIELD_AMOUNT).append("=");
            builder.append(GenericUtils.formatCoinValue(type, amount, true));
            questionMarkHasBeenOutput = true;
        }
        
        if (label != null && !"".equals(label)) {
            if (questionMarkHasBeenOutput) {
                builder.append(AMPERSAND_SEPARATOR);
            } else {
                builder.append(QUESTION_MARK_SEPARATOR);                
                questionMarkHasBeenOutput = true;
            }
            builder.append(FIELD_LABEL).append("=").append(encodeURLString(label));
        }
        
        if (message != null && !"".equals(message)) {
            if (questionMarkHasBeenOutput) {
                builder.append(AMPERSAND_SEPARATOR);
            } else {
                builder.append(QUESTION_MARK_SEPARATOR);
            }
            builder.append(FIELD_MESSAGE).append("=").append(encodeURLString(message));
        }

        if (pubkey != null && !"".equals(pubkey)) {
            if (questionMarkHasBeenOutput) {
                builder.append(AMPERSAND_SEPARATOR);
            } else {
                builder.append(QUESTION_MARK_SEPARATOR);
            }
            builder.append(FIELD_PUBKEY).append("=").append(encodeURLString(pubkey));
        }
        
        return builder.toString();
    }

    /**
     * Encode a string using URL encoding
     * 
     * @param stringToEncode The string to URL encode
     */
    static String encodeURLString(String stringToEncode) {
        try {
            return java.net.URLEncoder.encode(stringToEncode, "UTF-8").replace("+", ENCODED_SPACE_CHARACTER);
        } catch (UnsupportedEncodingException e) {
            // should not happen - UTF-8 is a valid encoding
            throw new RuntimeException(e);
        }
    }

    public String toUriString() {
        return convertToCoinURI(getAddress(), getAmount(), getLabel(), getMessage());
    }
}
