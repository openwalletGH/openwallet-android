package com.coinomi.stratumj;

/**
 * @author John L. Jegutanis
 */
final public class ServerAddress {
    final private String host;
    final private int port;

    public ServerAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "ServerAddress{" +
                "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
