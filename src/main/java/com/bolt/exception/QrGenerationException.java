package com.bolt.exception;

public class QrGenerationException extends RuntimeException {

    public QrGenerationException(String detail) {
        super(detail);
    }
}
