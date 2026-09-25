package com.toastedvr.toastedvr.backend.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageResolver messages;

    public GlobalExceptionHandler(MessageResolver messages) {
        this.messages = messages;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        // Todos los errores, uno por campo; el message general conserva el primero.
        Map<String, String> errorsByField = new LinkedHashMap<>();
        exception.getBindingResult()
            .getFieldErrors()
            .forEach(fieldError -> errorsByField.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        // Solo los nombres de campo: los valores pueden traer contraseñas.
        LOGGER.warn("Invalid request path={} fields={}", request.getRequestURI(), errorsByField.keySet());

        String message = errorsByField.values()
            .stream()
            .findFirst()
            .orElse(messages.get("error.validation.invalidRequest"));

        return buildResponse(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VALIDATION_ERROR,
            message,
            errorsByField.isEmpty() ? null : FieldErrors.of(errorsByField),
            request
        );
    }

    // JSON mal formado o con un valor que no corresponde al tipo (por ejemplo,
    // un resultado que no existe en el enum). No se audita: es un error del
    // cliente, no un intento de saltarse una regla de negocio.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn(
            "Unreadable request body path={} cause={}",
            request.getRequestURI(),
            exception.getMostSpecificCause().getClass().getSimpleName()
        );
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VALIDATION_ERROR,
            messages.get("error.validation.invalidRequest"),
            null,
            request
        );
    }

    // Parámetro de la URL con un valor que no corresponde a su tipo (por
    // ejemplo, ?result=EXCELENTE o /sessions/abc).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Invalid request parameter path={} parameter={}", request.getRequestURI(), exception.getName());
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VALIDATION_ERROR,
            messages.get("error.validation.invalidRequest"),
            null,
            request
        );
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.CONFLICT,
            ErrorCode.CONFLICT,
            messages.get("error.data.integrityViolation"),
            null,
            request
        );
    }

    @ExceptionHandler({
        InvalidVerificationCodeException.class,
        EmailDeliveryException.class,
        InvalidRequestException.class,
        RoastSessionRejectedException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(ApiException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception, request);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(
        AuthenticationFailedException exception,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, exception, request);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUsernameNotFound(
        UsernameNotFoundException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.UNAUTHORIZED,
            ErrorCode.AUTHENTICATION_FAILED,
            exception.getMessage(),
            null,
            request
        );
    }

    @ExceptionHandler({AccountBlockedException.class, EmailNotVerifiedException.class})
    public ResponseEntity<ApiErrorResponse> handleForbidden(ApiException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, exception, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
        ResourceNotFoundException exception,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, exception, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(Exception exception, HttpServletRequest request) {
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.INTERNAL_ERROR,
            messages.get("error.unexpected"),
            null,
            request
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
        HttpStatus status,
        ApiException exception,
        HttpServletRequest request
    ) {
        return buildResponse(status, exception.getCode(), exception.getMessage(), exception.getDetails(), request);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
        HttpStatus status,
        ErrorCode code,
        String message,
        Map<String, Object> details,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status.value()).body(
            new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                code,
                details
            )
        );
    }
}
