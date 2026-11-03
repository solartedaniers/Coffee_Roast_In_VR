package com.toastedvr.toastedvr.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
@SuppressWarnings("null")
class GlobalExceptionHandlerTests {

    private final MessageResolver messages = mock(MessageResolver.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messages);

    @Test
    void logsTheUnexpectedErrorAndKeepsTheGenericResponse(CapturedOutput output) {
        when(messages.get("error.unexpected")).thenReturn("Ocurrió un error inesperado en el servidor");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/roasting/simulations");

        ResponseEntity<ApiErrorResponse> response =
            handler.handleUnexpectedError(new IllegalStateException("fallo de prueba"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("Ocurrió un error inesperado en el servidor");
        assertThat(body.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(output.getOut())
            .contains("ERROR")
            .contains("/api/v1/roasting/simulations")
            .contains("IllegalStateException: fallo de prueba");
    }
}
