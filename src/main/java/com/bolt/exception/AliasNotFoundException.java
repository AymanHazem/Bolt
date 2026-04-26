package com.bolt.exception;

public class AliasNotFoundException extends RuntimeException {

    public AliasNotFoundException(String alias) {
        super("Alias not found: " + alias);
    }
}
