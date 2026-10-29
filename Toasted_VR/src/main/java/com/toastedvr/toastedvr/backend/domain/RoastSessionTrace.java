package com.toastedvr.toastedvr.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// Curva y decisiones del jugador que llegaron con la sesión, ya revisadas (RoastTraceInspector).
// Aparte de roasting_sessions para no cargarlas en el historial ni en el ranking.
@Entity
@Table(name = "roast_session_traces")
public class RoastSessionTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RoastingSession session;

    // El JSON tal como lo envió el cliente (RoastTraceRequest).
    @Column(name = "trace_json", nullable = false, columnDefinition = "text")
    private String traceJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RoastSessionTrace() {
    }

    public RoastSessionTrace(RoastingSession session, String traceJson) {
        this.session = session;
        this.traceJson = traceJson;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public RoastingSession getSession() {
        return session;
    }

    public String getTraceJson() {
        return traceJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
