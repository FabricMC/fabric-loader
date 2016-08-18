package net.fabricmc.api;

public enum Side {

    CLIENT,
    SERVER,
    UNIVERSAL;

    public boolean hasClient() {
        return this != SERVER;
    }

    public boolean hasServer() {
        return this != CLIENT;
    }

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }

}
