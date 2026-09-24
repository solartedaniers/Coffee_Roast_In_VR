package com.toastedvr.toastedvr.backend.exception;

import java.util.Map;

// Forma común de los errores por campo dentro de ApiErrorResponse.details:
// { "fieldErrors": { "campo": "mensaje" } }. El cliente los muestra debajo de cada input.
public final class FieldErrors {

    public static final String DETAILS_KEY = "fieldErrors";

    private FieldErrors() {
    }

    public static Map<String, Object> of(Map<String, String> errorsByField) {
        return Map.of(DETAILS_KEY, errorsByField);
    }

    public static Map<String, Object> of(String field, String message) {
        return of(Map.of(field, message));
    }
}
