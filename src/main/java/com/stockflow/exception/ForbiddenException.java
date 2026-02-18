package com.stockflow.exception;

public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String mensaje) {
        super(mensaje);
    }

    public ForbiddenException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}