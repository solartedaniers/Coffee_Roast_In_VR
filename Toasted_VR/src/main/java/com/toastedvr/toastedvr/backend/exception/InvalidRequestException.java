package com.toastedvr.toastedvr.backend.exception;

// Regla de validación que depende del estado guardado y no se puede expresar
// con anotaciones en el DTO (por ejemplo, validar el username solo si cambia).
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    /** Error que pertenece a un campo concreto del formulario. */
    public InvalidRequestException(String message, String field) {
        super(ErrorCode.VALIDATION_ERROR, message, FieldErrors.of(field, message), null);
    }
}
