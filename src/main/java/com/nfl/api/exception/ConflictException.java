package com.nfl.api.exception;

/** Conflito com o estado atual do recurso (duplicidade, vínculo existente) → HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
