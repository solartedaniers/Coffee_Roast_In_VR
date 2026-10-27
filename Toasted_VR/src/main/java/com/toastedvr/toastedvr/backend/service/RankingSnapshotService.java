package com.toastedvr.toastedvr.backend.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RankingSnapshot;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.RankingSnapshotRepository;
import org.springframework.stereotype.Service;

// Lee y guarda la última foto del ranking que vio cada jugador por nivel.
@Service
public class RankingSnapshotService {

    private static final TypeReference<Map<Long, Integer>> TOP_TYPE = new TypeReference<>() { };

    private final RankingSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public RankingSnapshotService(RankingSnapshotRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<RankingPhoto> find(Long userId, KnowledgeLevel level) {
        return repository.findByUserIdAndKnowledgeLevel(userId, level)
            .map(snapshot -> new RankingPhoto(readTop(snapshot.getTopPositions()), snapshot.getOwnPosition()));
    }

    public void save(User user, KnowledgeLevel level, RankingPhoto photo) {
        RankingSnapshot snapshot = repository.findByUserIdAndKnowledgeLevel(user.getId(), level)
            .orElseGet(() -> new RankingSnapshot(user, level));
        snapshot.replace(writeTop(photo.topPositions()), photo.ownPosition(), LocalDateTime.now());
        repository.save(snapshot);
    }

    private Map<Long, Integer> readTop(String json) {
        try {
            return objectMapper.readValue(json, TOP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid ranking snapshot.", exception);
        }
    }

    private String writeTop(Map<Long, Integer> topPositions) {
        try {
            return objectMapper.writeValueAsString(topPositions);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store ranking snapshot.", exception);
        }
    }
}
