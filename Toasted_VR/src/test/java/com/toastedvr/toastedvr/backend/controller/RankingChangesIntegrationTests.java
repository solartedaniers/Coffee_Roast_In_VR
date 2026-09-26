package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.RankingSnapshotRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF017: punto de aviso en la corona y flechas ▲▼/"Nuevo" respecto a la
// última foto del ranking que vio cada jugador en cada nivel.
@SpringBootTest
@AutoConfigureMockMvc
class RankingChangesIntegrationTests {

    private static final String PASSWORD = "Password123!";
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final KnowledgeLevel LEVEL = KnowledgeLevel.INTERMEDIATE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoastingSessionRepository roastingSessionRepository;

    @Autowired
    private RankingSnapshotRepository rankingSnapshotRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User viewer;
    private int minute;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        minute = 0;
        viewer = saveUser("viewerUser", Role.PLAYER, LEVEL);
    }

    @AfterEach
    void cleanDatabase() {
        rankingSnapshotRepository.deleteAll();
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldShowNoArrowsAndNoDotBeforeTheFirstVisitAndOnlyStoreThePhotoOnIt() throws Exception {
        saveSession(saveUser("anaUser", Role.PLAYER, LEVEL), 90);
        saveSession(viewer, 70);

        changes(viewer).andExpect(jsonPath("$.hasChanges").value(false));
        JsonNode ranking = ranking(viewer, "");
        assertThat(ranking.at("/top/0/movement").isNull()).isTrue();
        assertThat(ranking.at("/top/1/movement").isNull()).isTrue();
        assertThat(ranking.at("/me/movement").isNull()).isTrue();

        markSeen(viewer, ranking.get("signature").asText()).andExpect(status().isNoContent());

        assertThat(rankingSnapshotRepository.count()).isEqualTo(1);
        changes(viewer)
            .andExpect(jsonPath("$.level").value("INTERMEDIATE"))
            .andExpect(jsonPath("$.hasChanges").value(false))
            .andExpect(jsonPath("$.pollIntervalSeconds").value(60));
    }

    @Test
    void shouldLightTheDotAndShowUpDownAndNewSinceTheLastVisit() throws Exception {
        User ana = saveUser("anaUser", Role.PLAYER, LEVEL);
        User beto = saveUser("betoUser", Role.PLAYER, LEVEL);
        saveSession(ana, 90);
        saveSession(beto, 80);
        saveSession(viewer, 70);
        viewAndMarkSeen(viewer);

        // Entra Carla arriba y el jugador mejora: 1 Carla, 2 viewer, 3 Ana, 4 Beto.
        saveSession(saveUser("carlaUser", Role.PLAYER, LEVEL), 95);
        saveSession(viewer, 92);

        changes(viewer).andExpect(jsonPath("$.hasChanges").value(true));
        JsonNode ranking = ranking(viewer, "");
        assertMovement(ranking.at("/top/0"), "carlaUser", "NEW", 0);
        assertMovement(ranking.at("/top/1"), "viewerUser", "UP", 1);
        assertMovement(ranking.at("/top/2"), "anaUser", "DOWN", 2);
        assertMovement(ranking.at("/top/3"), "betoUser", "DOWN", 2);
        assertThat(ranking.at("/me/movement/direction").asText()).isEqualTo("UP");
        assertThat(ranking.at("/me/movement/places").asInt()).isEqualTo(1);
    }

    @Test
    void shouldTurnTheDotOffOnceTheRankingIsSeenAndShowNoMovementAfterwards() throws Exception {
        saveSession(saveUser("anaUser", Role.PLAYER, LEVEL), 90);
        saveSession(viewer, 70);
        viewAndMarkSeen(viewer);
        saveSession(saveUser("betoUser", Role.PLAYER, LEVEL), 80);
        changes(viewer).andExpect(jsonPath("$.hasChanges").value(true));

        viewAndMarkSeen(viewer);

        changes(viewer).andExpect(jsonPath("$.hasChanges").value(false));
        JsonNode again = ranking(viewer, "");
        again.get("top").forEach(entry -> assertThat(entry.at("/movement/direction").asText()).isEqualTo("SAME"));
    }

    @Test
    void shouldStoreExactlyTheSignedPhotoAndKeepTheDotWhenTheRankingChangedMeanwhile() throws Exception {
        saveSession(saveUser("anaUser", Role.PLAYER, LEVEL), 90);
        saveSession(viewer, 70);
        viewAndMarkSeen(viewer);

        // El jugador abre el ranking cuando ya entró Carla...
        saveSession(saveUser("carlaUser", Role.PLAYER, LEVEL), 95);
        String shownSignature = ranking(viewer, "").get("signature").asText();
        // ...y antes de que se marque como visto entra Diego.
        saveSession(saveUser("diegoUser", Role.PLAYER, LEVEL), 99);

        markSeen(viewer, shownSignature).andExpect(status().isNoContent());

        // Diego no se vio: el punto sigue encendido y solo él aparece como nuevo.
        changes(viewer).andExpect(jsonPath("$.hasChanges").value(true));
        JsonNode ranking = ranking(viewer, "");
        assertMovement(ranking.at("/top/0"), "diegoUser", "NEW", 0);
        assertMovement(ranking.at("/top/1"), "carlaUser", "DOWN", 1);
    }

    @Test
    void shouldCountLeavingTheTopAsAChange() throws Exception {
        for (int rival = 0; rival < 10; rival++) {
            saveSession(saveUser("rival" + rival, Role.PLAYER, LEVEL), 81 + rival);
        }
        saveSession(viewer, 50);
        viewAndMarkSeen(viewer);

        // Entra alguien arriba y rival0 sale del top 10.
        saveSession(saveUser("newcomer", Role.PLAYER, LEVEL), 99);

        changes(viewer).andExpect(jsonPath("$.hasChanges").value(true));
        JsonNode ranking = ranking(viewer, "");
        assertMovement(ranking.at("/top/0"), "newcomer", "NEW", 0);
        ranking.get("top").forEach(entry -> assertThat(entry.get("username").asText()).isNotEqualTo("rival0"));
    }

    @Test
    void shouldCountAChangeOfTheOwnPositionEvenOutsideTheTop() throws Exception {
        for (int rival = 0; rival < 10; rival++) {
            saveSession(saveUser("rival" + rival, Role.PLAYER, LEVEL), 81 + rival);
        }
        saveSession(viewer, 50);
        viewAndMarkSeen(viewer);

        // Alguien queda entre el top y el jugador: el top no cambia, la posición propia sí.
        saveSession(saveUser("middle", Role.PLAYER, LEVEL), 60);

        changes(viewer).andExpect(jsonPath("$.hasChanges").value(true));
        JsonNode ranking = ranking(viewer, "");
        assertThat(ranking.at("/me/position").asInt()).isEqualTo(12);
        assertThat(ranking.at("/me/movement/direction").asText()).isEqualTo("DOWN");
        assertThat(ranking.at("/me/movement/places").asInt()).isEqualTo(1);
    }

    @Test
    void shouldRejectForgedTamperedOrForeignSignatures() throws Exception {
        saveSession(viewer, 70);
        User other = saveUser("otherUser", Role.PLAYER, LEVEL);
        saveSession(other, 60);
        String otherSignature = ranking(other, "").get("signature").asText();
        String ownSignature = ranking(viewer, "").get("signature").asText();
        // Se altera un carácter del medio (el último puede ser solo relleno de base64).
        int middle = ownSignature.length() / 2;
        char replacement = ownSignature.charAt(middle) == 'A' ? 'B' : 'A';
        String tampered = ownSignature.substring(0, middle) + replacement + ownSignature.substring(middle + 1);

        for (String signature : new String[] {"not-a-signature", tampered, otherSignature}) {
            markSeen(viewer, signature)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("No se pudo marcar el ranking como visto. Vuelve a abrirlo."));
        }
        markSeen(viewer, "").andExpect(status().isBadRequest());
        assertThat(rankingSnapshotRepository.count()).isZero();
    }

    @Test
    void shouldNotExposeUserIdsInTheSignature() throws Exception {
        saveSession(viewer, 70);

        String signature = ranking(viewer, "").get("signature").asText();

        String decoded = new String(Base64.getUrlDecoder().decode(signature), StandardCharsets.ISO_8859_1);
        assertThat(decoded).doesNotContain("userId", "topPositions", "viewerUser", "INTERMEDIATE");
    }

    @Test
    void shouldKeepAPhotoPerLevelAndUseTheOwnLevelForTheDot() throws Exception {
        User beginner = saveUser("beginnerUser", Role.PLAYER, KnowledgeLevel.BEGINNER);
        saveSession(beginner, 80, KnowledgeLevel.BEGINNER);
        saveSession(viewer, 70);
        viewAndMarkSeen(viewer);

        // Ver otro nivel guarda su propia foto y no toca la del nivel del jugador.
        markSeen(viewer, ranking(viewer, "?level=BEGINNER").get("signature").asText()).andExpect(status().isNoContent());
        assertThat(rankingSnapshotRepository.count()).isEqualTo(2);

        saveSession(saveUser("newBeginner", Role.PLAYER, KnowledgeLevel.BEGINNER), 95, KnowledgeLevel.BEGINNER);
        changes(viewer).andExpect(jsonPath("$.hasChanges").value(false));

        saveSession(saveUser("newIntermediate", Role.PLAYER, LEVEL), 95);
        changes(viewer).andExpect(jsonPath("$.hasChanges").value(true));
    }

    @Test
    void shouldNeverLightTheDotForAdminsOrPlayersWithoutLevel() throws Exception {
        User admin = saveUser("adminUser", Role.ADMIN, LEVEL);
        saveSession(viewer, 70);
        viewAndMarkSeen(admin);
        saveSession(saveUser("anaUser", Role.PLAYER, LEVEL), 90);

        changes(admin).andExpect(jsonPath("$.hasChanges").value(false));

        viewer.updateKnowledgeLevel(null);
        userRepository.save(viewer);
        changes(viewer)
            .andExpect(jsonPath("$.hasChanges").value(false))
            .andExpect(jsonPath("$.level").isEmpty());
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/roasting/ranking/changes")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/roasting/ranking/seen")
                .contentType(jsonMediaType())
                .content("{\"signature\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }

    private void assertMovement(JsonNode entry, String username, String direction, int places) {
        assertThat(entry.get("username").asText()).isEqualTo(username);
        assertThat(entry.at("/movement/direction").asText()).isEqualTo(direction);
        assertThat(entry.at("/movement/places").asInt()).isEqualTo(places);
    }

    private void viewAndMarkSeen(User user) throws Exception {
        markSeen(user, ranking(user, "").get("signature").asText()).andExpect(status().isNoContent());
    }

    private JsonNode ranking(User user, String query) throws Exception {
        String body = mockMvc.perform(get("/api/v1/roasting/ranking" + query).header("Authorization", bearer(user)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return Objects.requireNonNull(objectMapper.readTree(body));
    }

    private ResultActions changes(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/roasting/ranking/changes").header("Authorization", bearer(user)))
            .andExpect(status().isOk());
    }

    private ResultActions markSeen(User user, String signature) throws Exception {
        return mockMvc.perform(put("/api/v1/roasting/ranking/seen")
            .header("Authorization", bearer(user))
            .contentType(jsonMediaType())
            .content(Objects.requireNonNull(objectMapper.writeValueAsString(Map.of("signature", signature)))));
    }

    private void saveSession(User owner, int score) {
        saveSession(owner, score, LEVEL);
    }

    private void saveSession(User owner, int score, KnowledgeLevel level) {
        RoastingSession session = roastingSessionRepository.save(new RoastingSession(
            owner, 190.0, 200.0, 780, 205.0, 206.0, RoastingResult.PERFECT, score, true, 150, level
        ));
        // Fechas crecientes y fijas: los empates y el orden no dependen del reloj.
        jdbcTemplate.update(
            "UPDATE roasting_sessions SET created_at = ? WHERE id = ?",
            BASE_TIME.plusMinutes(minute++),
            session.getId()
        );
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }

    private User saveUser(String username, Role role, KnowledgeLevel level) {
        User user = new User("Player", username.toLowerCase() + "@toastedvr.test", username, passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        user.assignRole(role);
        user.updateKnowledgeLevel(level);
        return userRepository.save(Objects.requireNonNull(user));
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON);
    }
}
