package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.OneTimeCodeRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.service.EmailService;
import com.toastedvr.toastedvr.backend.validation.EmailDomainLookup;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Reglas del formulario de registro: el nombre solo lleva letras y el correo
// debe tener un dominio real (sin errores de escritura y existente en el DNS).
@SpringBootTest(properties = "app.email-domain.dns-check-enabled=true")
@AutoConfigureMockMvc
class RegistrationRulesIntegrationTests {

    private static final String PASSWORD = "Password123!";
    private static final String LETTERS_ONLY = "Este campo solo acepta letras.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OneTimeCodeRepository oneTimeCodeRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @MockitoBean
    private EmailService emailService;

    // Sin red en los tests: el DNS se simula. Solo "noexiste.com" no existe.
    @MockitoBean
    private EmailDomainLookup emailDomainLookup;

    @BeforeEach
    void setUp() {
        oneTimeCodeRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
        when(emailDomainLookup.exists(anyString())).thenReturn(true);
        when(emailDomainLookup.exists("noexiste.com")).thenReturn(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Juan123", "Juan_Pérez", "Ana-María", "María!", "O'Neil", "Pedro 2"})
    void shouldRejectNamesWithNumbersOrSpecialCharacters(String name) throws Exception {
        register(name, "juan@gmail.com", "juanito")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors.name").value(LETTERS_ONLY));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void shouldAcceptNamesWithAccentsAndSpaces() throws Exception {
        register("José Ñúñez Gómez", "jose@gmail.com", "jose_2024!")
            .andExpect(status().isCreated());

        assertThat(userRepository.findByEmailIgnoreCase("jose@gmail.com").orElseThrow().getName())
            .isEqualTo("José Ñúñez Gómez");
    }

    @ParameterizedTest
    @ValueSource(strings = {"gail.com", "gmial.com", "gmail.con", "gmail.co", "hotmial.com", "outlok.com", "yaho.com"})
    void shouldRejectMisspelledProviderDomainsWithASuggestion(String domain) throws Exception {
        register("Juan Perez", "juan@" + domain, "juanito")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors.email")
                .value(Objects.requireNonNull(startsWith("Correo inexistente: el dominio «" + domain + "» no existe."))));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void shouldSuggestTheRightAddress() throws Exception {
        register("Juan Perez", "juan.perez@gail.com", "juanito")
            .andExpect(jsonPath("$.details.fieldErrors.email")
                .value("Correo inexistente: el dominio «gail.com» no existe. ¿Quisiste decir juan.perez@gmail.com?"));
    }

    @Test
    void shouldRejectDomainsThatDoNotExist() throws Exception {
        register("Juan Perez", "juan@noexiste.com", "juanito")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.fieldErrors.email")
                .value("Correo inexistente: el dominio «noexiste.com» no existe. Revisa tu correo."));

        assertThat(userRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gmail.com", "outlook.com", "hotmail.com", "udenar.edu.co", "empresa.com.co"})
    void shouldAcceptRealDomains(String domain) throws Exception {
        register("Juan Perez", "juan@" + domain, "juanito")
            .andExpect(status().isCreated());
    }

    @Test
    void shouldLetTheUsernameUseAnyCharacters() throws Exception {
        register("Juan Perez", "juan@gmail.com", "Ju4n_#Pérez!")
            .andExpect(status().isCreated());
    }

    private ResultActions register(String name, String email, String username) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of("name", name, "email", email, "username", username, "password", PASSWORD)))
        );
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON);
    }

    private @NonNull String requireJson(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value));
    }
}
