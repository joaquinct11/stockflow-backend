package com.stockflow.exception;

public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String mensaje) {
        super(mensaje);
    }

    public ConflictException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}