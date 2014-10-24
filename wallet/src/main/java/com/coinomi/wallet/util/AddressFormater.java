package com.coinomi.wallet.util;

/**
 * @author Giannis Dzegoutanis
 */
public class AddressFormater {

    public static String eightGroups(String address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address.substring(0, 4));
        sb.append(" ");
        sb.append(address.substring(4, 8));
        sb.append(" ");
        sb.append(address.substring(8, 12));
        sb.append(" ");
        sb.append(address.substring(12, 17));
        sb.append("\n");
        sb.append(address.substring(17, 21));
        sb.append(" ");
        sb.append(address.substring(21, 25));
        sb.append(" ");
        sb.append(address.substring(25, 29));
        sb.append(" ");
        sb.append(address.substring(29));

        return sb.toString();
    }
}
