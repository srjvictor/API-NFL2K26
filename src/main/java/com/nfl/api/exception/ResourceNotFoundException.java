package com.nfl.api.exception;

/** Recurso inexistente → HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " com id " + id + " não encontrado(a)");
    }
}
