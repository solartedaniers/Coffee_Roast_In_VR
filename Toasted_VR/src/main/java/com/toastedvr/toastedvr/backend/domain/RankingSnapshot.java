package com.toastedvr.toastedvr.backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// Última "foto" del ranking que vio un jugador en un nivel (RF017): la
// posición de cada usuario del top (por id, nunca se envía al cliente) y la
// posición propia. Sirve para el punto de aviso y las flechas ▲▼/"Nuevo".
@Entity
@Table(
    name = "ranking_snapshots",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "knowledge_level"})
)
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_level", nullable = false, length = 20)
    private KnowledgeLevel knowledgeLevel;

    // JSON {"idUsuario": posición} del top visto.
    @Column(name = "top_positions", nullable = false, columnDefinition = "text")
    private String topPositions;

    // Null si el jugador no aparecía en el ranking cuando lo vio.
    @Column(name = "own_position")
    private Integer ownPosition;

    @Column(name = "seen_at", nullable = false)
    private LocalDateTime seenAt;

    protected RankingSnapshot() {
    }

    public RankingSnapshot(User user, KnowledgeLevel knowledgeLevel) {
        this.user = user;
        this.knowledgeLevel = knowledgeLevel;
    }

    public void replace(String topPositions, Integer ownPosition, LocalDateTime seenAt) {
        this.topPositions = topPositions;
        this.ownPosition = ownPosition;
        this.seenAt = seenAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public KnowledgeLevel getKnowledgeLevel() {
        return knowledgeLevel;
    }

    public String getTopPositions() {
        return topPositions;
    }

    public Integer getOwnPosition() {
        return ownPosition;
    }

    public LocalDateTime getSeenAt() {
        return seenAt;
    }
}
