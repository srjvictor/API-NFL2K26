package com.nfl.api.exception;

/** Dados sintaticamente válidos mas que violam uma regra de negócio → HTTP 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
