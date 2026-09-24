package com.toastedvr.toastedvr.backend.exception;

// Código estable que acompaña a cada error de la API para que los clientes
// decidan qué mostrar sin depender del texto del mensaje.
public enum ErrorCode {
    VALIDATION_ERROR,
    CONFLICT,
    NOT_FOUND,
    AUTHENTICATION_FAILED,
    UNAUTHORIZED,
    FORBIDDEN,
    EMAIL_NOT_VERIFIED,
    ACCOUNT_BLOCKED,
    INVALID_CODE,
    EMAIL_DELIVERY_FAILED,
    INTERNAL_ERROR
}
