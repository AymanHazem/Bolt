package com.bolt.exception;

public class AliasAlreadyTakenException extends RuntimeException {

    public AliasAlreadyTakenException(String alias) {
        super("Alias already taken: " + alias);
    }
}
